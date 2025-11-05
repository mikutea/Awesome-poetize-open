package com.ld.poetry.im.websocket;

import com.ld.poetry.im.http.entity.ImChatUserGroupMessage;
import com.ld.poetry.im.http.entity.ImChatUserMessage;
import com.ld.poetry.im.http.service.ImChatUserGroupMessageService;
import com.ld.poetry.im.http.service.ImChatUserMessageService;
import com.ld.poetry.utils.mail.MailSendUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


@Component
@Slf4j
public class MessageCache {

    @Autowired
    private ImChatUserMessageService imChatUserMessageService;

    @Autowired
    private ImChatUserGroupMessageService imChatUserGroupMessageService;

    @Autowired
    private MailSendUtil mailSendUtil;

    private final List<ImChatUserMessage> userMessage = new ArrayList<>();

    private final List<ImChatUserGroupMessage> groupMessage = new ArrayList<>();

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    public void putUserMessage(ImChatUserMessage message) {
        // 修复：写操作必须使用写锁，不是读锁！
        readWriteLock.writeLock().lock();
        try {
            userMessage.add(message);
        } finally {
            readWriteLock.writeLock().unlock();
        }

        try {
            mailSendUtil.sendImMail(message);
        } catch (Exception e) {
            log.error("发送IM邮件失败：", e);
        }
    }

    public void putGroupMessage(ImChatUserGroupMessage message) {
        // 修复：写操作必须使用写锁，不是读锁！
        readWriteLock.writeLock().lock();
        try {
            groupMessage.add(message);
        } finally {
            readWriteLock.writeLock().unlock();
        }

    }

    @Scheduled(fixedDelay = 5000)
    public void saveUserMessage() {
        // 优化缓存策略：先复制并清空，减少锁持有时间
        List<ImChatUserMessage> messagesToSave = new ArrayList<>();
        
        readWriteLock.writeLock().lock();
        try {
            if (!CollectionUtils.isEmpty(userMessage)) {
                messagesToSave.addAll(userMessage);
                userMessage.clear();
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
        
        // 在锁外执行数据库操作，提高并发性能
        if (!messagesToSave.isEmpty()) {
            try {
                saveUserMessageWithTransaction(messagesToSave);
            } catch (Exception e) {
                log.error("批量保存用户消息失败，消息数量: {}", messagesToSave.size(), e);
                // 保存失败，将消息重新加入队列
                readWriteLock.writeLock().lock();
                try {
                    userMessage.addAll(0, messagesToSave);
                    log.warn("保存失败的 {} 条消息已重新加入队列", messagesToSave.size());
                } finally {
                    readWriteLock.writeLock().unlock();
                }
            }
        }
    }
    
    /**
     * 带事务的消息保存方法
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessageWithTransaction(List<ImChatUserMessage> messages) {
        imChatUserMessageService.saveBatch(messages);
    }

    @Scheduled(fixedDelay = 10000)
    public void saveGroupMessage() {
        // 优化缓存策略：先复制并清空，减少锁持有时间
        List<ImChatUserGroupMessage> messagesToSave = new ArrayList<>();
        
        readWriteLock.writeLock().lock();
        try {
            if (!CollectionUtils.isEmpty(groupMessage)) {
                messagesToSave.addAll(groupMessage);
                groupMessage.clear();
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
        
        // 在锁外执行数据库操作，提高并发性能
        if (!messagesToSave.isEmpty()) {
            try {
                saveGroupMessageWithTransaction(messagesToSave);
            } catch (Exception e) {
                log.error("批量保存群组消息失败，消息数量: {}", messagesToSave.size(), e);
                // 保存失败，将消息重新加入队列
                readWriteLock.writeLock().lock();
                try {
                    groupMessage.addAll(0, messagesToSave);
                    log.warn("保存失败的 {} 条消息已重新加入队列", messagesToSave.size());
                } finally {
                    readWriteLock.writeLock().unlock();
                }
            }
        }
    }
    
    /**
     * 带事务的群组消息保存方法
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveGroupMessageWithTransaction(List<ImChatUserGroupMessage> messages) {
        imChatUserGroupMessageService.saveBatch(messages);
    }
}
