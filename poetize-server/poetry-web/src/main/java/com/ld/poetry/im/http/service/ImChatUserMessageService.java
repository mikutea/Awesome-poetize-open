package com.ld.poetry.im.http.service;

import com.ld.poetry.im.http.entity.ImChatUserMessage;
import com.ld.poetry.im.http.vo.LastMessageVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 单聊记录 服务类
 * </p>
 *
 * @author sara
 * @since 2021-12-02
 */
public interface ImChatUserMessageService extends IService<ImChatUserMessage> {

    /**
     * 获取与指定好友的最后一条消息
     * @param currentUserId 当前用户ID
     * @param friendId 好友ID
     * @return 最后一条消息
     */
    LastMessageVO getLastMessageWithFriend(Integer currentUserId, Integer friendId);
}
