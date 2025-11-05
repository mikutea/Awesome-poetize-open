package com.ld.poetry.im.http.service.impl;

import com.ld.poetry.im.http.entity.ImChatUserGroupMessage;
import com.ld.poetry.im.http.dao.ImChatUserGroupMessageMapper;
import com.ld.poetry.im.http.service.ImChatUserGroupMessageService;
import com.ld.poetry.im.http.vo.LastMessageVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 群聊记录 服务实现类
 * </p>
 *
 * @author sara
 * @since 2021-12-02
 */
@Service
public class ImChatUserGroupMessageServiceImpl extends ServiceImpl<ImChatUserGroupMessageMapper, ImChatUserGroupMessage> implements ImChatUserGroupMessageService {

    @Override
    public LastMessageVO getLastGroupMessage(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        
        // 查询群组的最后一条消息，只查询需要的字段
        ImChatUserGroupMessage lastMessage = lambdaQuery()
            .select(ImChatUserGroupMessage::getContent, ImChatUserGroupMessage::getCreateTime)
            .eq(ImChatUserGroupMessage::getGroupId, groupId)
            .orderByDesc(ImChatUserGroupMessage::getCreateTime)
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
