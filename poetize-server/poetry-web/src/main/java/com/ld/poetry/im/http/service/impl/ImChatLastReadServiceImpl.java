package com.ld.poetry.im.http.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.im.http.dao.ImChatLastReadMapper;
import com.ld.poetry.im.http.entity.ImChatLastRead;
import com.ld.poetry.im.http.service.ImChatLastReadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 聊天最后查看时间 服务实现类（私聊+群聊）
 * </p>
 *
 * @author LeapYa
 * @since 2025-10-09
 */
@Slf4j
@Service
public class ImChatLastReadServiceImpl extends ServiceImpl<ImChatLastReadMapper, ImChatLastRead> implements ImChatLastReadService {

    @Override
    public Map<Integer, Integer> getFriendUnreadCounts(Integer userId) {
        try {
            List<Map<String, Object>> results = baseMapper.getFriendUnreadCountsByUserId(userId);
            Map<Integer, Integer> unreadCounts = new HashMap<>();
            
            for (Map<String, Object> result : results) {
                Integer friendId = (Integer) result.get("friend_id");
                Long unreadCount = (Long) result.get("unread_count");
                unreadCounts.put(friendId, unreadCount != null ? unreadCount.intValue() : 0);
            }
            
            return unreadCounts;
        } catch (Exception e) {
            log.error("获取用户 {} 的好友未读数失败", userId, e);
            return new HashMap<>();
        }
    }

    @Override
    public Map<Integer, Integer> getGroupUnreadCounts(Integer userId) {
        try {
            List<Map<String, Object>> results = baseMapper.getGroupUnreadCountsByUserId(userId);
            Map<Integer, Integer> unreadCounts = new HashMap<>();
            
            for (Map<String, Object> result : results) {
                Integer groupId = (Integer) result.get("group_id");
                Long unreadCount = (Long) result.get("unread_count");
                unreadCounts.put(groupId, unreadCount != null ? unreadCount.intValue() : 0);
            }
            
            return unreadCounts;
        } catch (Exception e) {
            log.error("获取用户 {} 的群聊未读数失败", userId, e);
            return new HashMap<>();
        }
    }

    @Override
    public Integer getFriendUnreadCount(Integer userId, Integer friendId) {
        try {
            Integer count = baseMapper.getFriendUnreadCount(userId, friendId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取用户 {} 和好友 {} 的未读数失败", userId, friendId, e);
            return 0;
        }
    }

    @Override
    public Integer getGroupUnreadCount(Integer userId, Integer groupId) {
        try {
            Integer count = baseMapper.getGroupUnreadCount(userId, groupId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取用户 {} 在群 {} 的未读数失败", userId, groupId, e);
            return 0;
        }
    }

    @Override
    public void markFriendAsRead(Integer userId, Integer friendId) {
        try {
            ImChatLastRead record = this.lambdaQuery()
                    .eq(ImChatLastRead::getUserId, userId)
                    .eq(ImChatLastRead::getChatType, ImChatLastRead.CHAT_TYPE_FRIEND)
                    .eq(ImChatLastRead::getChatId, friendId)
                    .one();
            
            if (record != null) {
                record.setLastReadTime(LocalDateTime.now());
                this.updateById(record);
            } else {
                record = new ImChatLastRead();
                record.setUserId(userId);
                record.setChatType(ImChatLastRead.CHAT_TYPE_FRIEND);
                record.setChatId(friendId);
                record.setLastReadTime(LocalDateTime.now());
                this.save(record);
            }
            
        } catch (Exception e) {
            log.error("标记用户 {} 和好友 {} 的消息为已读失败", userId, friendId, e);
        }
    }

    @Override
    public void markGroupAsRead(Integer userId, Integer groupId) {
        try {
            ImChatLastRead record = this.lambdaQuery()
                    .eq(ImChatLastRead::getUserId, userId)
                    .eq(ImChatLastRead::getChatType, ImChatLastRead.CHAT_TYPE_GROUP)
                    .eq(ImChatLastRead::getChatId, groupId)
                    .one();
            
            if (record != null) {
                record.setLastReadTime(LocalDateTime.now());
                this.updateById(record);
            } else {
                record = new ImChatLastRead();
                record.setUserId(userId);
                record.setChatType(ImChatLastRead.CHAT_TYPE_GROUP);
                record.setChatId(groupId);
                record.setLastReadTime(LocalDateTime.now());
                this.save(record);
            }
            
        } catch (Exception e) {
            log.error("标记用户 {} 在群 {} 的消息为已读失败", userId, groupId, e);
        }
    }

    @Override
    public List<Integer> getFriendChatList(Integer userId) {
        try {
            List<Integer> friendList = baseMapper.getFriendChatList(userId);
            return friendList != null ? friendList : new ArrayList<>();
        } catch (Exception e) {
            log.error("获取用户 {} 的私聊列表失败", userId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Integer> getGroupChatList(Integer userId) {
        try {
            List<Integer> groupList = baseMapper.getGroupChatList(userId);
            return groupList != null ? groupList : new ArrayList<>();
        } catch (Exception e) {
            log.error("获取用户 {} 的群聊列表失败", userId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void hideFriendChat(Integer userId, Integer friendId) {
        try {
            ImChatLastRead record = this.lambdaQuery()
                    .eq(ImChatLastRead::getUserId, userId)
                    .eq(ImChatLastRead::getChatType, ImChatLastRead.CHAT_TYPE_FRIEND)
                    .eq(ImChatLastRead::getChatId, friendId)
                    .one();
            
            if (record != null) {
                record.setIsHidden(1);
                this.updateById(record);
            } else {
                // 创建隐藏记录
                record = new ImChatLastRead();
                record.setUserId(userId);
                record.setChatType(ImChatLastRead.CHAT_TYPE_FRIEND);
                record.setChatId(friendId);
                record.setLastReadTime(LocalDateTime.now());
                record.setIsHidden(1);
                this.save(record);
            }
            
        } catch (Exception e) {
            log.error("隐藏用户 {} 和好友 {} 的聊天失败", userId, friendId, e);
        }
    }

    @Override
    public void hideGroupChat(Integer userId, Integer groupId) {
        try {
            ImChatLastRead record = this.lambdaQuery()
                    .eq(ImChatLastRead::getUserId, userId)
                    .eq(ImChatLastRead::getChatType, ImChatLastRead.CHAT_TYPE_GROUP)
                    .eq(ImChatLastRead::getChatId, groupId)
                    .one();
            
            if (record != null) {
                record.setIsHidden(1);
                this.updateById(record);
            } else {
                // 创建隐藏记录
                record = new ImChatLastRead();
                record.setUserId(userId);
                record.setChatType(ImChatLastRead.CHAT_TYPE_GROUP);
                record.setChatId(groupId);
                record.setLastReadTime(LocalDateTime.now());
                record.setIsHidden(1);
                this.save(record);
            }
            
        } catch (Exception e) {
            log.error("隐藏用户 {} 在群 {} 的聊天失败", userId, groupId, e);
        }
    }

    @Override
    public void unhideChat(Integer userId, Integer chatType, Integer chatId) {
        try {
            ImChatLastRead record = this.lambdaQuery()
                    .eq(ImChatLastRead::getUserId, userId)
                    .eq(ImChatLastRead::getChatType, chatType)
                    .eq(ImChatLastRead::getChatId, chatId)
                    .one();
            
            if (record != null && record.getIsHidden() != null && record.getIsHidden() == 1) {
                record.setIsHidden(0);
                record.setLastReadTime(LocalDateTime.now()); // 更新查看时间，移到列表顶部
                this.updateById(record);
            }
        } catch (Exception e) {
            log.error("取消隐藏聊天失败 - userId: {}, chatType: {}, chatId: {}", userId, chatType, chatId, e);
        }
    }
}
