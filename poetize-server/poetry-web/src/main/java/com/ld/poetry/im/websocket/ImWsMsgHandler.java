package com.ld.poetry.im.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.im.http.entity.ImChatGroupUser;
import com.ld.poetry.im.http.entity.ImChatLastRead;
import com.ld.poetry.im.http.entity.ImChatUserGroupMessage;
import com.ld.poetry.im.http.entity.ImChatUserMessage;
import com.ld.poetry.im.http.service.ImChatGroupUserService;
import com.ld.poetry.im.http.service.ImChatUserMessageService;
import com.ld.poetry.im.http.service.ImChatUserGroupMessageService;
import com.ld.poetry.im.http.service.ImChatLastReadService;
import com.ld.poetry.im.http.vo.LastMessageVO;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.http.common.HttpRequest;
import org.tio.http.common.HttpResponse;
import org.tio.utils.lock.SetWithLock;
import org.tio.websocket.common.WsRequest;
import org.tio.websocket.common.WsResponse;
import org.tio.websocket.server.handler.IWsMsgHandler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.Semaphore;

@Component
@Slf4j
public class ImWsMsgHandler implements IWsMsgHandler {
    
    // 限制IM并发查询数量，避免耗尽数据库连接池
    // 无论连接池多大，IM功能限制在35个并发查询，为其他功能（REST API、定时任务）预留连接
    private static final int MAX_CONCURRENT_DB_QUERIES = 35;
    private final Semaphore dbQuerySemaphore = new Semaphore(MAX_CONCURRENT_DB_QUERIES);

    @Autowired
    private ImChatGroupUserService imChatGroupUserService;

    @Autowired
    private ImChatUserMessageService imChatUserMessageService;

    @Autowired
    private ImChatUserGroupMessageService imChatUserGroupMessageService;

    @Autowired
    private ImChatLastReadService imChatLastReadService;

    @Autowired
    private MessageCache messageCache;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private CacheService cacheService;

    /**
     * 握手时走这个方法，业务可以在这里获取cookie，request等
     * 对httpResponse参数进行补充并返回，如果返回null表示不想和对方建立连接
     * 对于大部分业务，该方法只需要一行代码：return httpResponse;
     */
    @Override
    public HttpResponse handshake(HttpRequest httpRequest, HttpResponse httpResponse, ChannelContext channelContext) {
        String token = httpRequest.getParam("token");

        if (!StringUtils.hasText(token)) {
            log.warn("WebSocket握手失败：token为空");
            return null;
        }

        try {
            // 首先使用SecureTokenGenerator验证token的完整性和有效性
            com.ld.poetry.utils.SecureTokenGenerator.TokenValidationResult validationResult = 
                com.ld.poetry.utils.SecureTokenGenerator.validateToken(token);
            
            if (!validationResult.isValid()) {
                log.warn("WebSocket握手失败：token验证失败 - {}, 原因: {}", token, validationResult.getErrorMessage());
                return null;
            }

            Integer userId = validationResult.getUserId();
            if (userId == null) {
                log.warn("WebSocket握手失败：无法从token中获取用户ID - {}", token);
                return null;
            }

            // 获取用户信息
            User user = cacheService.getCachedUser(userId);
            if (user == null) {
                // 如果缓存中没有用户信息，尝试从数据库获取
                user = commonQuery.getUser(userId);
                if (user == null) {
                    log.warn("WebSocket握手失败：用户信息不存在 - userId: {}", userId);
                    return null;
                }
                // 缓存用户信息
                cacheService.cacheUser(user);
            }

            log.info("WebSocket握手成功：用户ID：{}, 用户名：{}, token类型：{}", 
                user.getId(), user.getUsername(), validationResult.getUserType());
            return httpResponse;
        } catch (Exception e) {
            log.error("WebSocket握手时验证用户失败 - token: {}", token, e);
            return null;
        }
    }

    /**
     * 握手成功后触发该方法
     */
    @Override
    public void onAfterHandshaked(HttpRequest httpRequest, HttpResponse httpResponse, ChannelContext channelContext) {
        String token = httpRequest.getParam("token");
        final User user;

        try {
            // 使用SecureTokenGenerator验证token（与握手阶段保持一致）
            com.ld.poetry.utils.SecureTokenGenerator.TokenValidationResult validationResult = 
                com.ld.poetry.utils.SecureTokenGenerator.validateToken(token);
            
            if (!validationResult.isValid()) {
                log.warn("WebSocket连接绑定失败：token验证失败 - {}, 原因: {}", token, validationResult.getErrorMessage());
                return;
            }

            Integer userId = validationResult.getUserId();
            if (userId != null) {
                User tempUser = cacheService.getCachedUser(userId);
                if (tempUser == null) {
                    // 如果缓存中没有用户信息，尝试从数据库获取
                    tempUser = commonQuery.getUser(userId);
                    if (tempUser != null) {
                        // 缓存用户信息
                        cacheService.cacheUser(tempUser);
                    }
                }
                
                if (tempUser != null) {
                    user = tempUser;
                    // 关闭该用户的其他连接，确保单点登录
                    Tio.closeUser(channelContext.tioConfig, user.getId().toString(), null);
                    // 绑定用户到当前连接
                    Tio.bindUser(channelContext, user.getId().toString());
                    log.info("WebSocket连接绑定成功：用户ID：{}, 用户名：{}, token类型：{}", 
                        user.getId(), user.getUsername(), validationResult.getUserType());
                } else {
                    log.warn("WebSocket连接绑定失败：用户信息不存在 - userId: {}", userId);
                    return;
                }
            } else {
                log.warn("WebSocket连接绑定失败：无法从token中获取用户ID - {}", token);
                return;
            }
        } catch (Exception e) {
            log.error("WebSocket连接绑定时发生错误 - token: {}", token, e);
            return;
        }

        // 处理未读的用户消息
        try {
            List<ImChatUserMessage> userMessages = imChatUserMessageService.lambdaQuery().eq(ImChatUserMessage::getToId, user.getId())
                    .eq(ImChatUserMessage::getMessageStatus, ImConfigConst.USER_MESSAGE_STATUS_FALSE)
                    .orderByAsc(ImChatUserMessage::getCreateTime).list();

            if (!CollectionUtils.isEmpty(userMessages)) {
                userMessages.forEach(userMessage -> {
                    ImMessage imMessage = new ImMessage();
                    imMessage.setContent(userMessage.getContent());
                    imMessage.setFromId(userMessage.getFromId());
                    imMessage.setToId(userMessage.getToId());
                    imMessage.setMessageType(ImEnum.MESSAGE_TYPE_MSG_SINGLE.getCode());
                    User friend = commonQuery.getUser(userMessage.getFromId());
                    if (friend != null) {
                        imMessage.setAvatar(friend.getAvatar());
                    }
                    // 使用更安全的序列化配置
                    String jsonString = JSON.toJSONString(imMessage, 
                        SerializerFeature.WriteMapNullValue,
                        SerializerFeature.WriteNullStringAsEmpty,
                        SerializerFeature.WriteNonStringKeyAsString,
                        SerializerFeature.DisableCircularReferenceDetect);
                    WsResponse wsResponse = WsResponse.fromText(jsonString, ImConfigConst.CHARSET);
                    Tio.sendToUser(channelContext.tioConfig, userMessage.getToId().toString(), wsResponse);
                });
                
                // ❌ 不再立即标记为已读！改为用户进入聊天时才标记
                // imChatUserMessageService.lambdaUpdate().in(ImChatUserMessage::getId, ids)
                //         .set(ImChatUserMessage::getMessageStatus, ImConfigConst.USER_MESSAGE_STATUS_TRUE).update();
            }
        } catch (Exception e) {
            log.error("处理用户未读消息时发生错误 - userId: {}", user.getId(), e);
        }

        // 绑定用户所在的群组
        try {
            LambdaQueryChainWrapper<ImChatGroupUser> lambdaQuery = imChatGroupUserService.lambdaQuery();
            lambdaQuery.select(ImChatGroupUser::getGroupId);
            lambdaQuery.eq(ImChatGroupUser::getUserId, user.getId());
            lambdaQuery.in(ImChatGroupUser::getUserStatus, ImConfigConst.GROUP_USER_STATUS_PASS, ImConfigConst.GROUP_USER_STATUS_SILENCE);
            List<ImChatGroupUser> groupUsers = lambdaQuery.list();
            if (!CollectionUtils.isEmpty(groupUsers)) {
                groupUsers.forEach(groupUser -> Tio.bindGroup(channelContext, groupUser.getGroupId().toString()));
            }
        } catch (Exception e) {
            log.error("绑定用户群组时发生错误 - userId: {}", user.getId(), e);
        }

        // 推送聊天列表、私聊和群聊的未读消息数
        try (var scope = StructuredTaskScope.open()) {
            // 并行获取聊天列表和未读数
            Subtask<List<Integer>> friendChatListTask = scope.fork(() -> 
                imChatLastReadService.getFriendChatList(user.getId())
            );
            
            Subtask<List<Integer>> groupChatListTask = scope.fork(() -> 
                imChatLastReadService.getGroupChatList(user.getId())
            );
            
            Subtask<Map<Integer, Integer>> friendUnreadCountsTask = scope.fork(() -> 
                imChatLastReadService.getFriendUnreadCounts(user.getId())
            );
            
            Subtask<Map<Integer, Integer>> groupUnreadCountsTask = scope.fork(() -> 
                imChatLastReadService.getGroupUnreadCounts(user.getId())
            );
            
            // 等待所有基础数据获取完成
            scope.join();
            
            // 获取结果并确保不为null
            List<Integer> friendChatList = (friendChatListTask.state() == Subtask.State.SUCCESS && friendChatListTask.get() != null) 
                ? friendChatListTask.get() : new ArrayList<>();
            List<Integer> groupChatList = (groupChatListTask.state() == Subtask.State.SUCCESS && groupChatListTask.get() != null) 
                ? groupChatListTask.get() : new ArrayList<>();
            Map<Integer, Integer> friendUnreadCounts = (friendUnreadCountsTask.state() == Subtask.State.SUCCESS && friendUnreadCountsTask.get() != null) 
                ? friendUnreadCountsTask.get() : new HashMap<>();
            Map<Integer, Integer> groupUnreadCounts = (groupUnreadCountsTask.state() == Subtask.State.SUCCESS && groupUnreadCountsTask.get() != null) 
                ? groupUnreadCountsTask.get() : new HashMap<>();
            
            // 并行获取所有好友和群组的最后一条消息（限制并发数，避免耗尽连接池）
            Map<String, LastMessageVO> friendLastMessages = new ConcurrentHashMap<>();
            Map<String, LastMessageVO> groupLastMessages = new ConcurrentHashMap<>();
            
            try (var msgScope = StructuredTaskScope.open()) {
                // 为每个好友创建并行任务获取最后一条消息
                for (Integer friendId : friendChatList) {
                    msgScope.fork(() -> {
                        try {
                            // 获取信号量许可，限制并发数
                            dbQuerySemaphore.acquire();
                            try {
                                LastMessageVO lastMsg = imChatUserMessageService.getLastMessageWithFriend(user.getId(), friendId);
                                if (lastMsg != null) {
                                    friendLastMessages.put(friendId.toString(), lastMsg);
                                }
                            } finally {
                                // 释放信号量
                                dbQuerySemaphore.release();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("获取好友 {} 的最后一条消息被中断", friendId);
                        } catch (Exception e) {
                            log.error("获取好友 {} 的最后一条消息失败: {}", friendId, e.getMessage());
                        }
                        return null;
                    });
                }
                
                // 为每个群组创建并行任务获取最后一条消息
                for (Integer groupId : groupChatList) {
                    msgScope.fork(() -> {
                        try {
                            // 获取信号量许可，限制并发数
                            dbQuerySemaphore.acquire();
                            try {
                                LastMessageVO lastMsg = imChatUserGroupMessageService.getLastGroupMessage(groupId);
                                if (lastMsg != null) {
                                    groupLastMessages.put(groupId.toString(), lastMsg);
                                }
                            } finally {
                                // 释放信号量
                                dbQuerySemaphore.release();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("获取群组 {} 的最后一条消息被中断", groupId);
                        } catch (Exception e) {
                            log.error("获取群组 {} 的最后一条消息失败: {}", groupId, e.getMessage());
                        }
                        return null;
                    });
                }
                
                // 等待所有消息获取完成
                msgScope.join();
            }
            
            // 构造未读数和聊天列表消息
            Map<String, Object> syncMessage = new HashMap<>();
            syncMessage.put("messageType", 5); // 5表示同步消息（未读数+聊天列表）
            syncMessage.put("friendChatList", friendChatList);
            syncMessage.put("groupChatList", groupChatList);
            syncMessage.put("friendUnreadCounts", friendUnreadCounts);
            syncMessage.put("groupUnreadCounts", groupUnreadCounts);
            syncMessage.put("friendLastMessages", friendLastMessages);  // ✅ 新增：好友最后一条消息
            syncMessage.put("groupLastMessages", groupLastMessages);    // ✅ 新增：群聊最后一条消息
            
            // 使用更安全的序列化配置，确保正确处理特殊字符和空值
            String jsonString = JSON.toJSONString(syncMessage, 
                SerializerFeature.WriteMapNullValue,  // 写入null值
                SerializerFeature.WriteNullListAsEmpty,  // null列表输出为[]
                SerializerFeature.WriteNonStringKeyAsString,  // 非字符串key转为字符串（修复-1等数字key的问题）
                SerializerFeature.DisableCircularReferenceDetect);  // 禁用循环引用检测
            
            WsResponse wsResponse = WsResponse.fromText(jsonString, ImConfigConst.CHARSET);
            Tio.sendToUser(channelContext.tioConfig, user.getId().toString(), wsResponse);
                
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("推送聊天数据被中断 - userId: {}", user.getId(), e);
        } catch (Exception e) {
            log.error("推送聊天数据失败 - userId: {}, 错误信息: {}", user.getId(), e.getMessage(), e);
        }
        
        // 延迟广播在线用户数（使用虚拟线程，等待旧连接完全关闭）
        Thread.ofVirtual().start(() -> {
            try {
                // 延迟200ms，确保 closeUser 执行完成
                Thread.sleep(200);
                
                // 获取用户所在的群组并广播在线人数
                SetWithLock<String> groups = channelContext.getGroups();
                if (groups != null && groups.size() > 0) {
                    TioWebsocketStarter tioWebsocketStarter = TioUtil.getTio();
                    if (tioWebsocketStarter != null) {
                        for (String groupId : groups.getObj()) {
                            int onlineCount = Tio.getByGroup(tioWebsocketStarter.getServerTioConfig(), groupId).size();
                            
                            ImMessage imMessage = new ImMessage();
                            imMessage.setMessageType(CommonConst.ONLINE_COUNT_MESSAGE_TYPE);
                            imMessage.setOnlineCount(onlineCount);
                            imMessage.setGroupId(Integer.valueOf(groupId));
                            
                            WsResponse onlineWs = WsResponse.fromText(imMessage.toJsonString(), CommonConst.CHARSET_NAME);
                            Tio.sendToGroup(tioWebsocketStarter.getServerTioConfig(), groupId, onlineWs);
                            
                        }
                    }
                }
            } catch (Exception e) {
                log.error("延迟广播在线用户数失败 - userId: {}", user.getId(), e);
            }
        });
    }

    @Override
    public Object onBytes(WsRequest wsRequest, byte[] bytes, ChannelContext channelContext) {
        return null;
    }

    @Override
    public Object onClose(WsRequest wsRequest, byte[] bytes, ChannelContext channelContext) {
        Tio.remove(channelContext, "连接关闭");
        return null;
    }

    @Override
    public Object onText(WsRequest wsRequest, String text, ChannelContext channelContext) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            ImMessage imMessage = JSON.parseObject(text, ImMessage.class);

            String content = StringUtil.removeHtml(imMessage.getContent());
            if (!StringUtils.hasText(content)) {
                return null;
            }
            imMessage.setContent(content);

            // 使用更安全的序列化配置
            String jsonString = JSON.toJSONString(imMessage, 
                SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteNullStringAsEmpty,
                SerializerFeature.WriteNonStringKeyAsString,
                SerializerFeature.DisableCircularReferenceDetect);
            WsResponse wsResponse = WsResponse.fromText(jsonString, ImConfigConst.CHARSET);
            if (imMessage.getMessageType().intValue() == ImEnum.MESSAGE_TYPE_MSG_SINGLE.getCode()) {
                //单聊
                ImChatUserMessage userMessage = new ImChatUserMessage();
                userMessage.setFromId(imMessage.getFromId());
                userMessage.setToId(imMessage.getToId());
                userMessage.setContent(imMessage.getContent());
                userMessage.setCreateTime(LocalDateTime.now());

                // 自动取消隐藏（发送者和接收者）
                imChatLastReadService.unhideChat(imMessage.getFromId(), ImChatLastRead.CHAT_TYPE_FRIEND, imMessage.getToId());
                imChatLastReadService.unhideChat(imMessage.getToId(), ImChatLastRead.CHAT_TYPE_FRIEND, imMessage.getFromId());

                SetWithLock<ChannelContext> setWithLock = Tio.getByUserid(channelContext.tioConfig, imMessage.getToId().toString());
                if (setWithLock != null && setWithLock.size() > 0) {
                    Tio.sendToUser(channelContext.tioConfig, imMessage.getToId().toString(), wsResponse);
                    userMessage.setMessageStatus(ImConfigConst.USER_MESSAGE_STATUS_TRUE);
                } else {
                    userMessage.setMessageStatus(ImConfigConst.USER_MESSAGE_STATUS_FALSE);
                }
                messageCache.putUserMessage(userMessage);
                Tio.sendToUser(channelContext.tioConfig, imMessage.getFromId().toString(), wsResponse);
            } else if (imMessage.getMessageType().intValue() == ImEnum.MESSAGE_TYPE_MSG_GROUP.getCode()) {
                //群聊
                ImChatUserGroupMessage groupMessage = new ImChatUserGroupMessage();
                groupMessage.setContent(imMessage.getContent());
                groupMessage.setFromId(imMessage.getFromId());
                groupMessage.setGroupId(imMessage.getGroupId());
                groupMessage.setCreateTime(LocalDateTime.now());
                messageCache.putGroupMessage(groupMessage);

                // 🆕 自动取消隐藏（发送者）
                imChatLastReadService.unhideChat(imMessage.getFromId(), ImChatLastRead.CHAT_TYPE_GROUP, imMessage.getGroupId());

                SetWithLock<ChannelContext> setWithLock = Tio.getByGroup(channelContext.tioConfig, imMessage.getGroupId().toString());
                if (setWithLock != null && setWithLock.size() > 0) {
                    Tio.sendToGroup(channelContext.tioConfig, imMessage.getGroupId().toString(), wsResponse);
                }
            }
        } catch (Exception e) {
            log.error("解析消息失败：{}", e.getMessage());
        }
        //返回值是要发送给客户端的内容，一般都是返回null
        return null;
    }
}
