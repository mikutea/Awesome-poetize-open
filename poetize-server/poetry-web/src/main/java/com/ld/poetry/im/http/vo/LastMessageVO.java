package com.ld.poetry.im.http.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 最后一条消息 VO
 * 用于聊天列表同步时返回每个聊天的最后一条消息预览
 */
@Data
public class LastMessageVO {
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}

