package com.ld.poetry.im.http.service.impl;

import com.ld.poetry.im.http.entity.ImChatUserMessage;
import com.ld.poetry.im.http.dao.ImChatUserMessageMapper;
import com.ld.poetry.im.http.service.ImChatUserMessageService;
import com.ld.poetry.im.http.vo.LastMessageVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 单聊记录 服务实现类
 * </p>
 *
 * @author sara
 * @since 2021-12-02
 */
@Service
public class ImChatUserMessageServiceImpl extends ServiceImpl<ImChatUserMessageMapper, ImChatUserMessage> implements ImChatUserMessageService {

    @Override
    public LastMessageVO getLastMessageWithFriend(Integer currentUserId, Integer friendId) {
        if (currentUserId == null || friendId == null) {
            return null;
        }
        
        // 查询最后一条消息（发送或接收），只查询需要的字段
        ImChatUserMessage lastMessage = lambdaQuery()
            .select(ImChatUserMessage::getContent, ImChatUserMessage::getCreateTime)
            .and(wrapper -> wrapper
                .and(w -> w.eq(ImChatUserMessage::getFromId, currentUserId).eq(ImChatUserMessage::getToId, friendId))
                .or(w -> w.eq(ImChatUserMessage::getFromId, friendId).eq(ImChatUserMessage::getToId, currentUserId))
            )
            .orderByDesc(ImChatUserMessage::getCreateTime)
            .last("LIMIT 1")
            .one();
        
        if (lastMessage == null) {
            return null;
        }
        
        // 构造返回对象
        LastMessageVO vo = new LastMessageVO();
        vo.setContent(lastMessage.getContent());
        vo.setCreateTime(lastMessage.getCreateTime());
        
        return vo;
    }
}
