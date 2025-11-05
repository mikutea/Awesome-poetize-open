package com.ld.poetry.im.http.service;

import com.ld.poetry.im.http.entity.ImChatUserGroupMessage;
import com.ld.poetry.im.http.vo.LastMessageVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 群聊记录 服务类
 * </p>
 *
 * @author sara
 * @since 2021-12-02
 */
public interface ImChatUserGroupMessageService extends IService<ImChatUserGroupMessage> {

    /**
     * 获取群组的最后一条消息
     * @param groupId 群组ID
     * @return 最后一条消息
     */
    LastMessageVO getLastGroupMessage(Integer groupId);
}
