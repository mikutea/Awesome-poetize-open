package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.aop.SaveCheck;
import com.ld.poetry.service.CommentService;
import com.ld.poetry.service.LocationService;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.utils.StringUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.CommentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;


/**
 * <p>
 * 文章评论表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@Slf4j
@RestController
@RequestMapping("/comment")
public class CommentController {


    @Autowired
    private CommentService commentService;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private LocationService locationService;


    /**
     * 保存评论
     */
    @PostMapping("/saveComment")
    @LoginCheck
    @SaveCheck
    public PoetryResult saveComment(@Validated @RequestBody CommentVO commentVO) {
        String content = StringUtil.removeHtml(commentVO.getCommentContent());
        if (!StringUtils.hasText(content)) {
            return PoetryResult.fail("评论内容不合法！");
        }
        commentVO.setCommentContent(content);

        PoetryCache.remove(CommonConst.COMMENT_COUNT_CACHE + commentVO.getSource().toString() + "_" + commentVO.getType());
        return commentService.saveComment(commentVO);
    }


    /**
     * 删除评论
     */
    @GetMapping("/deleteComment")
    @LoginCheck
    public PoetryResult deleteComment(@RequestParam("id") Integer id) {
        return commentService.deleteComment(id);
    }


    /**
     * 查询评论数量
     */
    @GetMapping("/getCommentCount")
    public PoetryResult<Integer> getCommentCount(@RequestParam("source") Integer source, @RequestParam("type") String type) {
        return PoetryResult.success(commonQuery.getCommentCount(source, type));
    }


    /**
     * 查询评论
     */
    @PostMapping("/listComment")
    public PoetryResult<BaseRequestVO> listComment(@RequestBody BaseRequestVO baseRequestVO) {
        return commentService.listComment(baseRequestVO);
    }

    /**
     * 🔧 新接口：子评论懒加载查询
     * 支持分页加载某个评论的子评论
     */
    @PostMapping("/listChildComments")
    public PoetryResult<Page<CommentVO>> listChildComments(
            @RequestParam("parentCommentId") Integer parentCommentId,
            @RequestBody BaseRequestVO baseRequestVO,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {

        PoetryResult<Page<CommentVO>> result = commentService.listChildComments(parentCommentId, baseRequestVO, current, size);

        return result;
    }

    /**
     * 获取IP地理位置缓存统计信息 - 管理员功能
     */
    @GetMapping("/getLocationCacheStats")
    public PoetryResult<Map<String, Object>> getLocationCacheStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("cacheSize", locationService.getCacheSize());
        result.put("message", "IP地理位置缓存统计信息");
        return PoetryResult.success(result);
    }

    /**
     * 清理IP地理位置缓存 - 管理员功能
     */
    @PostMapping("/clearLocationCache")
    public PoetryResult<String> clearLocationCache() {
        locationService.clearLocationCache();
        return PoetryResult.success("IP地理位置缓存已清理");
    }
}

