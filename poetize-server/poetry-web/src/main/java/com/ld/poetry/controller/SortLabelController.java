package com.ld.poetry.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.PrerenderClient;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

        // 检查分类名称是否已存在（唯一性校验）
        Sort existingSort = sortMapper.selectOne(new QueryWrapper<Sort>().eq("sort_name", sort.getSortName()));
        if (existingSort != null) {
            return PoetryResult.fail("分类名称已存在，请使用其他名称！");
        }

        sortMapper.insert(sort);
        log.info("分类新增成功，分类名称: {}", sort.getSortName());

        // 分类新增后，重新渲染首页和分类索引页面
        try {
            prerenderClient.renderHomePage();
            prerenderClient.renderSortIndexPage();
            log.debug("分类新增后成功触发页面预渲染");
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("分类新增后页面预渲染失败", e);
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
        log.info("分类删除成功，分类ID: {}", id);

        // 分类删除后，删除对应分类页面的预渲染文件，并重新渲染首页和分类索引页面
        try {
            prerenderClient.deleteCategoryPage(id);
            prerenderClient.renderHomePage();
            prerenderClient.renderSortIndexPage();
            log.debug("分类删除后成功触发页面预渲染");
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("分类删除后页面预渲染失败", e);
        }

        return PoetryResult.success();
    }


    /**
     * 更新
     */
    @PostMapping("/updateSort")
    @LoginCheck(0)
    public PoetryResult updateSort(@RequestBody Sort sort) {
        if (!StringUtils.hasText(sort.getSortName()) || !StringUtils.hasText(sort.getSortDescription())) {
            return PoetryResult.fail("分类名称和分类描述不能为空！");
        }

        if (sort.getPriority() == null) {
            return PoetryResult.fail("分类必须配置优先级！");
        }

        // 检查分类名称是否已存在（唯一性校验），排除当前分类
        Sort existingSort = sortMapper.selectOne(new QueryWrapper<Sort>()
                .eq("sort_name", sort.getSortName())
                .ne("id", sort.getId()));
        if (existingSort != null) {
            return PoetryResult.fail("分类名称已存在，请使用其他名称！");
        }

        sortMapper.updateById(sort);
        log.info("分类更新成功，分类ID: {}", sort.getId());

        // 分类更新后，重新渲染对应分类页面、首页和分类索引页面
        try {
            if (sort.getId() != null) {
                prerenderClient.renderCategoryPage(sort.getId());
            }
            prerenderClient.renderHomePage();
            prerenderClient.renderSortIndexPage();
            log.debug("分类更新后成功触发页面预渲染");
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("分类更新后页面预渲染失败", e);
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

        // 检查在同一分类下标签名称是否已存在（唯一性校验）
        Label existingLabel = labelMapper.selectOne(new QueryWrapper<Label>()
                .eq("label_name", label.getLabelName())
                .eq("sort_id", label.getSortId()));
        if (existingLabel != null) {
            return PoetryResult.fail("该分类下已存在相同的标签名称，请使用其他名称！");
        }

        labelMapper.insert(label);
        log.info("标签新增成功，标签名称: {}", label.getLabelName());

        // 标签新增后，重新渲染对应分类页面
        try {
            prerenderClient.renderCategoryPage(label.getSortId());
            log.debug("标签新增后成功触发页面预渲染");
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("标签新增后页面预渲染失败", e);
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
        log.info("标签删除成功，标签ID: {}", id);

        // 标签删除后，重新渲染对应分类页面
        if (label != null && label.getSortId() != null) {
            try {
                prerenderClient.renderCategoryPage(label.getSortId());
                log.debug("标签删除后成功触发页面预渲染");
            } catch (Exception e) {
                // 预渲染失败不影响主流程
                log.warn("标签删除后页面预渲染失败", e);
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
        if (!StringUtils.hasText(label.getLabelName()) || !StringUtils.hasText(label.getLabelDescription()) || label.getSortId() == null) {
            return PoetryResult.fail("标签名称和标签描述和分类Id不能为空！");
        }

        // 检查在同一分类下标签名称是否已存在（唯一性校验），排除当前标签
        Label existingLabel = labelMapper.selectOne(new QueryWrapper<Label>()
                .eq("label_name", label.getLabelName())
                .eq("sort_id", label.getSortId())
                .ne("id", label.getId()));
        if (existingLabel != null) {
            return PoetryResult.fail("该分类下已存在相同的标签名称，请使用其他名称！");
        }

        labelMapper.updateById(label);
        log.info("标签更新成功，标签ID: {}", label.getId());

        // 标签更新后，重新渲染对应分类页面
        try {
            if (label.getSortId() != null) {
                prerenderClient.renderCategoryPage(label.getSortId());
                log.debug("标签更新后成功触发页面预渲染");
            }
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("标签更新后页面预渲染失败", e);
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
