package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.PrerenderClient;
import com.ld.poetry.utils.cache.PoetryCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 分类标签 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@RestController
@RequestMapping("/webInfo")
public class SortLabelController {

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private PrerenderClient prerenderClient;

    /**
     * 获取分类标签信息
     */
    @GetMapping("/getSortInfo")
    public PoetryResult<List<Sort>> getSortInfo() {
        return PoetryResult.success(commonQuery.getSortInfo());
    }

    /**
     * 保存
     */
    @PostMapping("/saveSort")
    @LoginCheck(0)
    public PoetryResult saveSort(@RequestBody Sort sort) {
        if (!StringUtils.hasText(sort.getSortName()) || !StringUtils.hasText(sort.getSortDescription())) {
            return PoetryResult.fail("分类名称和分类描述不能为空！");
        }

        if (sort.getPriority() == null) {
            return PoetryResult.fail("分类必须配置优先级！");
        }

        sortMapper.insert(sort);
        PoetryCache.remove(CommonConst.SORT_INFO);
        
        // 分类新增后，重新渲染首页和默认分类页面
        try {
            prerenderClient.renderHomePage();
            prerenderClient.renderDefaultSortPage();
        } catch (Exception e) {
            // 预渲染失败不影响主流程
        }
        
        return PoetryResult.success();
    }


    /**
     * 删除
     */
    @GetMapping("/deleteSort")
    @LoginCheck(0)
    public PoetryResult deleteSort(@RequestParam("id") Integer id) {
        sortMapper.deleteById(id);
        PoetryCache.remove(CommonConst.SORT_INFO);
        
        // 分类删除后，删除对应分类页面的预渲染文件，并重新渲染首页和默认分类页面
        try {
            prerenderClient.deleteSortPage(id);
            prerenderClient.renderHomePage();
            prerenderClient.renderDefaultSortPage();
        } catch (Exception e) {
            // 预渲染失败不影响主流程
        }
        
        return PoetryResult.success();
    }


    /**
     * 更新
     */
    @PostMapping("/updateSort")
    @LoginCheck(0)
    public PoetryResult updateSort(@RequestBody Sort sort) {
        sortMapper.updateById(sort);
        PoetryCache.remove(CommonConst.SORT_INFO);
        
        // 分类更新后，重新渲染对应分类页面、首页和默认分类页面
        try {
            if (sort.getId() != null) {
                prerenderClient.renderSortPage(sort.getId());
            }
            prerenderClient.renderHomePage();
            prerenderClient.renderDefaultSortPage();
        } catch (Exception e) {
            // 预渲染失败不影响主流程
        }
        
        return PoetryResult.success();
    }


    /**
     * 查询List
     */
    @GetMapping("/listSort")
    public PoetryResult<List<Sort>> listSort() {
        return PoetryResult.success(new LambdaQueryChainWrapper<>(sortMapper).list());
    }


    /**
     * 保存
     */
    @PostMapping("/saveLabel")
    @LoginCheck(0)
    public PoetryResult saveLabel(@RequestBody Label label) {
        if (!StringUtils.hasText(label.getLabelName()) || !StringUtils.hasText(label.getLabelDescription()) || label.getSortId() == null) {
            return PoetryResult.fail("标签名称和标签描述和分类Id不能为空！");
        }
        labelMapper.insert(label);
        PoetryCache.remove(CommonConst.SORT_INFO);
        
        // 标签新增后，重新渲染对应分类页面
        try {
            prerenderClient.renderSortPage(label.getSortId());
        } catch (Exception e) {
            // 预渲染失败不影响主流程
        }
        
        return PoetryResult.success();
    }


    /**
     * 删除
     */
    @GetMapping("/deleteLabel")
    @LoginCheck(0)
    public PoetryResult deleteLabel(@RequestParam("id") Integer id) {
        // 先获取标签信息以获得分类ID
        Label label = labelMapper.selectById(id);
        
        labelMapper.deleteById(id);
        PoetryCache.remove(CommonConst.SORT_INFO);
        
        // 标签删除后，重新渲染对应分类页面
        if (label != null && label.getSortId() != null) {
            try {
                prerenderClient.renderSortPage(label.getSortId());
            } catch (Exception e) {
                // 预渲染失败不影响主流程
            }
        }
        
        return PoetryResult.success();
    }


    /**
     * 更新
     */
    @PostMapping("/updateLabel")
    @LoginCheck(0)
    public PoetryResult updateLabel(@RequestBody Label label) {
        labelMapper.updateById(label);
        PoetryCache.remove(CommonConst.SORT_INFO);
        
        // 标签更新后，重新渲染对应分类页面
        try {
            if (label.getSortId() != null) {
                prerenderClient.renderSortPage(label.getSortId());
            }
        } catch (Exception e) {
            // 预渲染失败不影响主流程
        }
        
        return PoetryResult.success();
    }


    /**
     * 查询List
     */
    @GetMapping("/listLabel")
    public PoetryResult<List<Label>> listLabel() {
        return PoetryResult.success(new LambdaQueryChainWrapper<>(labelMapper).list());
    }


    /**
     * 查询List
     */
    @GetMapping("/listSortAndLabel")
    public PoetryResult<Map> listSortAndLabel() {
        Map<String, List> map = new HashMap<>();
        map.put("sorts", new LambdaQueryChainWrapper<>(sortMapper).list());
        map.put("labels", new LambdaQueryChainWrapper<>(labelMapper).list());
        return PoetryResult.success(map);
    }
}
