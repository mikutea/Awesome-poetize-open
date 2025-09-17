package com.ld.poetry.im.websocket;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.entity.User;
import com.ld.poetry.im.http.entity.ImChatGroupUser;
import com.ld.poetry.im.http.entity.ImChatUserGroupMessage;
import com.ld.poetry.im.http.entity.ImChatUserMessage;
import com.ld.poetry.im.http.service.ImChatGroupUserService;
import com.ld.poetry.im.http.service.ImChatUserMessageService;
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
import java.util.List;

@Component
@Slf4j
public class ImWsMsgHandler implements IWsMsgHandler {

    @Autowired
    private ImChatGroupUserService imChatGroupUserService;

    @Autowired
    private ImChatUserMessageService imChatUserMessageService;

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
        User user = null;

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
                user = cacheService.getCachedUser(userId);
                if (user == null) {
                    // 如果缓存中没有用户信息，尝试从数据库获取
                    user = commonQuery.getUser(userId);
                    if (user != null) {
                        // 缓存用户信息
                        cacheService.cacheUser(user);
                    }
                }
                
                if (user != null) {
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
                List<Long> ids = new ArrayList<>();
                userMessages.forEach(userMessage -> {
                    ids.add(userMessage.getId());
                    ImMessage imMessage = new ImMessage();
                    imMessage.setContent(userMessage.getContent());
                    imMessage.setFromId(userMessage.getFromId());
                    imMessage.setToId(userMessage.getToId());
                    imMessage.setMessageType(ImEnum.MESSAGE_TYPE_MSG_SINGLE.getCode());
                    User friend = commonQuery.getUser(userMessage.getFromId());
                    if (friend != null) {
                        imMessage.setAvatar(friend.getAvatar());
                    }
                    WsResponse wsResponse = WsResponse.fromText(JSON.toJSONString(imMessage), ImConfigConst.CHARSET);
                    Tio.sendToUser(channelContext.tioConfig, userMessage.getToId().toString(), wsResponse);
                });
                imChatUserMessageService.lambdaUpdate().in(ImChatUserMessage::getId, ids)
                        .set(ImChatUserMessage::getMessageStatus, ImConfigConst.USER_MESSAGE_STATUS_TRUE).update();
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
                log.debug("绑定用户群组成功 - userId: {}, 群组数量: {}", user.getId(), groupUsers.size());
            }
        } catch (Exception e) {
            log.error("绑定用户群组时发生错误 - userId: {}", user.getId(), e);
        }
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

            WsResponse wsResponse = WsResponse.fromText(JSON.toJSONString(imMessage), ImConfigConst.CHARSET);
            if (imMessage.getMessageType().intValue() == ImEnum.MESSAGE_TYPE_MSG_SINGLE.getCode()) {
                //单聊
                ImChatUserMessage userMessage = new ImChatUserMessage();
                userMessage.setFromId(imMessage.getFromId());
                userMessage.setToId(imMessage.getToId());
                userMessage.setContent(imMessage.getContent());
                userMessage.setCreateTime(LocalDateTime.now());

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
