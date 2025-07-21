package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.CommentMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.Comment;
import com.ld.poetry.entity.User;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.enums.CommentTypeEnum;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.CommentService;
import com.ld.poetry.service.LocationService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.mail.MailSendUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.CommentVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 文章评论表 服务实现类
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@Slf4j
@SuppressWarnings("unchecked")
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private MailSendUtil mailSendUtil;

    @Autowired
    private LocationService locationService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private UserService userService;

    @Override
    @CacheEvict(value = "comments", key = "#commentVO.source + '_' + #commentVO.type")
    public PoetryResult saveComment(CommentVO commentVO) {
        if (CommentTypeEnum.getEnumByCode(commentVO.getType()) == null) {
            return PoetryResult.fail("评论来源类型不存在！");
        }
        Article one = null;
        if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(commentVO.getType())) {
            LambdaQueryChainWrapper<Article> articleWrapper = new LambdaQueryChainWrapper<>(articleMapper);
            one = articleWrapper.eq(Article::getId, commentVO.getSource()).select(Article::getUserId, Article::getArticleTitle, Article::getCommentStatus).one();

            if (one == null) {
                return PoetryResult.fail("文章不存在");
            } else {
                if (!one.getCommentStatus()) {
                    return PoetryResult.fail("评论功能已关闭！");
                }
            }
        }


        Comment comment = new Comment();
        comment.setSource(commentVO.getSource());
        comment.setType(commentVO.getType());
        comment.setCommentContent(commentVO.getCommentContent());
        comment.setParentCommentId(commentVO.getParentCommentId());

        // 🔧 修复：正确设置floorCommentId
        Integer floorCommentId = calculateFloorCommentId(commentVO.getParentCommentId(), commentVO.getFloorCommentId());
        comment.setFloorCommentId(floorCommentId);

        comment.setParentUserId(commentVO.getParentUserId());
        comment.setUserId(PoetryUtil.getUserId());
        if (StringUtils.hasText(commentVO.getCommentInfo())) {
            comment.setCommentInfo(commentVO.getCommentInfo());
        }

        // 获取IP地址和地理位置
        String clientIp = PoetryUtil.getCurrentClientIp();
        comment.setIpAddress(clientIp);

        // 获取地理位置
        String location = locationService.getLocationByIp(clientIp);
        comment.setLocation(location);

        save(comment);

        try {
            mailSendUtil.sendCommentMail(commentVO, one, this);
        } catch (Exception e) {
            log.error("发送评论邮件失败：", e);
        }

        // 清理评论相关缓存
        cacheService.evictCommentRelatedCache(commentVO.getSource(), commentVO.getType());

        return PoetryResult.success();
    }

    /**
     * 🔧 新方法：正确计算floorCommentId
     * @param parentCommentId 父评论ID
     * @param frontendFloorCommentId 前端传递的floorCommentId（用于验证）
     * @return 正确的floorCommentId
     */
    private Integer calculateFloorCommentId(Integer parentCommentId, Integer frontendFloorCommentId) {
        // 如果是一级评论（parentCommentId为0或null），floorCommentId应该为null
        if (parentCommentId == null || parentCommentId.equals(CommonConst.FIRST_COMMENT)) {
            log.debug("[DEBUG] 🏢 一级评论，floorCommentId设置为null");
            return null;
        }

        // 递归查找最顶层的一级评论
        Integer floorCommentId = findFloorCommentId(parentCommentId);

        // 验证前端传递的floorCommentId是否正确
        if (frontendFloorCommentId != null && !frontendFloorCommentId.equals(floorCommentId)) {
            log.warn("[WARN] ⚠️ 前端传递的floorCommentId({})与计算结果({})不一致，使用计算结果",
                    frontendFloorCommentId, floorCommentId);
        }

        log.debug("[DEBUG] 🏢 计算得到floorCommentId: {}", floorCommentId);
        return floorCommentId;
    }

    /**
     * 🔧 递归查找指定评论的楼层ID（一级评论ID）
     * @param commentId 评论ID
     * @return 楼层ID（一级评论ID）
     */
    private Integer findFloorCommentId(Integer commentId) {
        Comment comment = getById(commentId);
        if (comment == null) {
            log.error("[ERROR] ❌ 找不到评论ID: {}", commentId);
            return null;
        }

        // 如果是一级评论，返回自己的ID
        if (comment.getParentCommentId() == null || comment.getParentCommentId().equals(CommonConst.FIRST_COMMENT)) {
            log.debug("[DEBUG] 🏢 找到一级评论ID: {}", comment.getId());
            return comment.getId();
        }

        // 递归查找父评论的楼层ID
        return findFloorCommentId(comment.getParentCommentId());
    }

    @Override
    @CacheEvict(value = "comments", allEntries = true)
    public PoetryResult deleteComment(Integer id) {
        Integer userId = PoetryUtil.getUserId();

        // 获取评论信息用于清理缓存
        Comment comment = getById(id);

        lambdaUpdate().eq(Comment::getId, id)
                .eq(Comment::getUserId, userId)
                .remove();

        // 清理评论相关缓存
        if (comment != null) {
            cacheService.evictCommentRelatedCache(comment.getSource(), comment.getType());
        }

        return PoetryResult.success();
    }

    @Override
    public PoetryResult<BaseRequestVO> listComment(BaseRequestVO baseRequestVO) {
        if (baseRequestVO.getSource() == null || !StringUtils.hasText(baseRequestVO.getCommentType())) {
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(baseRequestVO.getCommentType())) {
            LambdaQueryChainWrapper<Article> articleWrapper = new LambdaQueryChainWrapper<>(articleMapper);
            Article one = articleWrapper.eq(Article::getId, baseRequestVO.getSource()).select(Article::getCommentStatus).one();

            if (one != null && !one.getCommentStatus()) {
                return PoetryResult.fail("评论功能已关闭！");
            }
        }

        if (baseRequestVO.getFloorCommentId() == null) {
            // 🔧 新设计：主评论支持分页查询，提升性能和用户体验
            long queryStartTime = System.currentTimeMillis();

            // 创建分页对象，支持一级评论分页
            Page<Comment> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
            page.addOrder(OrderItem.desc("create_time")); // 按创建时间倒序

            // 🚀 优化：分页查询一级评论，只查询必要字段
            Page<Comment> mainCommentsPage = lambdaQuery()
                .select(Comment::getId, Comment::getSource, Comment::getType,
                       Comment::getParentCommentId, Comment::getParentUserId,
                       Comment::getUserId, Comment::getLikeCount, Comment::getCommentContent,
                       Comment::getCommentInfo, Comment::getIpAddress, Comment::getLocation,
                       Comment::getFloorCommentId, Comment::getCreateTime) // 只查询必要字段
                .eq(Comment::getSource, baseRequestVO.getSource())
                .eq(Comment::getType, baseRequestVO.getCommentType())
                .eq(Comment::getParentCommentId, CommonConst.FIRST_COMMENT)
                .page(page);

            long queryEndTime = System.currentTimeMillis();
            log.debug("一级评论分页查询耗时: {}ms, 查询到{}条记录",
                queryEndTime - queryStartTime, mainCommentsPage.getRecords().size());

            if (CollectionUtils.isEmpty(mainCommentsPage.getRecords())) {
                baseRequestVO.setRecords(new ArrayList<>());
                baseRequestVO.setTotal(0);
                return PoetryResult.success(baseRequestVO);
            }

            // 性能优化：使用批量查询构建主评论VO，解决N+1查询问题
            List<CommentVO> commentVOs = buildMainCommentVOsWithBatchStats(
                mainCommentsPage.getRecords(),
                baseRequestVO.getSource(),
                baseRequestVO.getCommentType()
            );

            baseRequestVO.setRecords(commentVOs);
            baseRequestVO.setTotal((int) mainCommentsPage.getTotal()); // 使用分页查询的总数
        } else {
            // 查询楼层的所有回复（使用floorCommentId）
            Page<Comment> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
            page.addOrder(OrderItem.asc("create_time"));

            Page<Comment> resultPage = lambdaQuery()
                .eq(Comment::getSource, baseRequestVO.getSource())
                .eq(Comment::getType, baseRequestVO.getCommentType())
                .eq(Comment::getFloorCommentId, baseRequestVO.getFloorCommentId())  // 查询该楼层的所有回复
                .page(page);

            List<Comment> childComments = resultPage.getRecords();
            if (CollectionUtils.isEmpty(childComments)) {
                baseRequestVO.setRecords(new ArrayList<>());
                baseRequestVO.setTotal(0);
                return PoetryResult.success(baseRequestVO);
            }

            // 修复：为每个楼层回复也构建childComments字段，最大深度为4（确保加载所有深层评论）
            List<CommentVO> ccVO = childComments.stream()
                .map(cc -> buildCommentVOWithChildren(cc, baseRequestVO, 4))
                .collect(Collectors.toList());

            baseRequestVO.setRecords(ccVO);
            baseRequestVO.setTotal(resultPage.getTotal());
        }

        return PoetryResult.success(baseRequestVO);
    }

    @Override
    public PoetryResult<Page> listAdminComment(BaseRequestVO baseRequestVO, Boolean isBoss) {
        LambdaQueryChainWrapper<Comment> wrapper = lambdaQuery();
        if (isBoss) {
            if (baseRequestVO.getSource() != null) {
                wrapper.eq(Comment::getSource, baseRequestVO.getSource());
            }
            if (StringUtils.hasText(baseRequestVO.getCommentType())) {
                wrapper.eq(Comment::getType, baseRequestVO.getCommentType());
            }
            Page<Comment> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
            wrapper.orderByDesc(Comment::getCreateTime).page(page);
            baseRequestVO.setRecords(page.getRecords());
            baseRequestVO.setTotal(page.getTotal());
        } else {
            List<Integer> userArticleIds = commonQuery.getUserArticleIds(PoetryUtil.getUserId());
            if (CollectionUtils.isEmpty(userArticleIds)) {
                baseRequestVO.setTotal(0);
                baseRequestVO.setRecords(new ArrayList());
            } else {
                if (baseRequestVO.getSource() != null) {
                    wrapper.eq(Comment::getSource, baseRequestVO.getSource()).eq(Comment::getType, CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode());
                } else {
                    wrapper.eq(Comment::getType, CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode()).in(Comment::getSource, userArticleIds);
                }
                Page<Comment> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
                wrapper.orderByDesc(Comment::getCreateTime).page(page);
                baseRequestVO.setRecords(page.getRecords());
                baseRequestVO.setTotal(page.getTotal());
            }
        }
        return PoetryResult.success(baseRequestVO);
    }

    private CommentVO buildCommentVO(Comment c) {
        CommentVO commentVO = new CommentVO();
        BeanUtils.copyProperties(c, commentVO);

        User user = commonQuery.getUser(commentVO.getUserId());
        if (user != null) {
            commentVO.setAvatar(user.getAvatar());
            commentVO.setUsername(user.getUsername());
            log.debug("[DEBUG] 🔧 用户信息设置完成 - 用户名:{}, 头像:{}", user.getUsername(), user.getAvatar());
        } else {
            log.debug("[DEBUG] ⚠️ 未找到用户信息 - 用户ID:{}", commentVO.getUserId());
        }

        if (!StringUtils.hasText(commentVO.getUsername())) {
            String randomName = PoetryUtil.getRandomName(commentVO.getUserId().toString());
            commentVO.setUsername(randomName);
        }

        if (commentVO.getParentUserId() != null) {
            User u = commonQuery.getUser(commentVO.getParentUserId());
            if (u != null) {
                commentVO.setParentUsername(u.getUsername());
                log.debug("[DEBUG] 🔧 父用户信息设置完成 - 父用户名:{}", u.getUsername());
            } else {
                log.debug("[DEBUG] ⚠️ 未找到父用户信息 - 父用户ID:{}", commentVO.getParentUserId());
            }
            if (!StringUtils.hasText(commentVO.getParentUsername())) {
                String randomParentName = PoetryUtil.getRandomName(commentVO.getParentUserId().toString());
                commentVO.setParentUsername(randomParentName);
                log.debug("[DEBUG] 🔧 设置随机父用户名:{}", randomParentName);
            }
        }

        log.debug("[DEBUG] ✅ CommentVO构建完成 - ID:{}, 用户名:{}, 父用户名:{}",
                 commentVO.getId(), commentVO.getUsername(), commentVO.getParentUsername());
        return commentVO;
    }

    /**
     * 批量构建主评论VO列表，解决N+1查询问题
     *
     * @param mainComments 主评论列表
     * @param source 评论来源
     * @param type 评论类型
     * @return 构建完成的CommentVO列表
     */
    private List<CommentVO> buildMainCommentVOsWithBatchStats(List<Comment> mainComments, Integer source, String type) {
        if (CollectionUtils.isEmpty(mainComments)) {
            return new ArrayList<>();
        }

        // 批量收集所有需要查询的用户ID
        Set<Integer> allUserIds = new HashSet<>();
        for (Comment comment : mainComments) {
            if (comment.getUserId() != null) {
                allUserIds.add(comment.getUserId());
            }
            if (comment.getParentUserId() != null) {
                allUserIds.add(comment.getParentUserId());
            }
        }

        // 一次性批量查询所有用户信息
        Map<Integer, User> userMap = batchGetUsers(new ArrayList<>(allUserIds));

        // 提取所有主评论ID
        List<Integer> mainCommentIds = new ArrayList<>(mainComments.size());
        for (Comment comment : mainComments) {
            mainCommentIds.add(comment.getId());
        }

        // 批量查询所有主评论的子评论统计
        Map<Integer, Long> childCountMap = batchCountNestedChildren(mainCommentIds, source, type);

        // 批量构建CommentVO
        List<CommentVO> commentVOs = new ArrayList<>(mainComments.size());
        for (Comment comment : mainComments) {
            CommentVO commentVO = buildCommentVOOptimized(comment, userMap);

            // 从批量查询结果中获取子评论数量
            Long totalChildCount = childCountMap.getOrDefault(comment.getId(), 0L);

            if (totalChildCount > 0) {
                Page<CommentVO> childCommentsPage = createEmptyPage(totalChildCount);
                commentVO.setChildComments(childCommentsPage);
            }

            commentVOs.add(commentVO);
        }

        return commentVOs;
    }

    /**
     * 批量查询用户信息，解决用户查询N+1问题
     */
    private Map<Integer, User> batchGetUsers(List<Integer> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return new HashMap<>();
        }

        Map<Integer, User> userMap = new HashMap<>();

        try {
            // 先尝试从缓存批量获取
            List<Integer> uncachedUserIds = new ArrayList<>();
            for (Integer userId : userIds) {
                User cachedUser = cacheService.getCachedUser(userId);
                if (cachedUser != null) {
                    userMap.put(userId, cachedUser);
                } else {
                    uncachedUserIds.add(userId);
                }
            }

            // 批量查询未缓存的用户
            if (!uncachedUserIds.isEmpty()) {
                // 使用MyBatis-Plus的in查询，只查询必要字段
                List<User> users = userService.lambdaQuery()
                    .select(User::getId, User::getUsername, User::getAvatar)
                    .in(User::getId, uncachedUserIds)
                    .list();

                // 批量缓存新查询的用户
                for (User user : users) {
                    userMap.put(user.getId(), user);
                    cacheService.cacheUser(user);
                }
            }

        } catch (Exception e) {
            log.warn("批量用户查询失败，降级为逐个查询: {}", e.getMessage());
            // 降级处理：逐个查询
            userMap.clear();
            for (Integer userId : userIds) {
                User user = commonQuery.getUser(userId);
                if (user != null) {
                    userMap.put(userId, user);
                }
            }
        }

        return userMap;
    }

    /**
     * 高效构建CommentVO，使用预查询的用户信息
     */
    private CommentVO buildCommentVOOptimized(Comment comment, Map<Integer, User> userMap) {
        CommentVO commentVO = new CommentVO();

        // 直接设置字段，避免BeanUtils反射开销
        commentVO.setId(comment.getId());
        commentVO.setSource(comment.getSource());
        commentVO.setType(comment.getType());
        commentVO.setParentCommentId(comment.getParentCommentId());
        commentVO.setParentUserId(comment.getParentUserId());
        commentVO.setUserId(comment.getUserId());
        commentVO.setLikeCount(comment.getLikeCount());
        commentVO.setCommentContent(comment.getCommentContent());
        commentVO.setCommentInfo(comment.getCommentInfo());
        commentVO.setIpAddress(comment.getIpAddress());
        commentVO.setLocation(comment.getLocation());
        commentVO.setFloorCommentId(comment.getFloorCommentId());
        commentVO.setCreateTime(comment.getCreateTime());

        // 从预查询的用户Map中获取用户信息
        User user = userMap.get(comment.getUserId());
        if (user != null) {
            commentVO.setAvatar(user.getAvatar());
            commentVO.setUsername(user.getUsername());
        }

        if (!StringUtils.hasText(commentVO.getUsername())) {
            commentVO.setUsername(PoetryUtil.getRandomName(comment.getUserId().toString()));
        }

        // 处理父用户信息
        if (comment.getParentUserId() != null) {
            User parentUser = userMap.get(comment.getParentUserId());
            if (parentUser != null) {
                commentVO.setParentUsername(parentUser.getUsername());
            }

            if (!StringUtils.hasText(commentVO.getParentUsername())) {
                commentVO.setParentUsername(PoetryUtil.getRandomName(comment.getParentUserId().toString()));
            }
        }

        return commentVO;
    }

    /**
     * 创建空的分页对象
     */
    private Page<CommentVO> createEmptyPage(Long total) {
        Page<CommentVO> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(total);
        return page;
    }



    /**
     * 批量统计多个主评论的嵌套子评论数量
     *
     * @param mainCommentIds 主评论ID列表
     * @param source 评论来源
     * @param type 评论类型
     * @return Map<主评论ID, 嵌套子评论总数>
     */
    private Map<Integer, Long> batchCountNestedChildren(List<Integer> mainCommentIds, Integer source, String type) {
        if (CollectionUtils.isEmpty(mainCommentIds)) {
            return new HashMap<>();
        }

        // 只查询必要字段，减少数据传输量
        List<Comment> allChildComments = lambdaQuery()
            .select(Comment::getFloorCommentId)
            .eq(Comment::getSource, source)
            .eq(Comment::getType, type)
            .in(Comment::getFloorCommentId, mainCommentIds)
            .ne(Comment::getParentCommentId, CommonConst.FIRST_COMMENT)
            .list();

        // 使用高效的分组统计
        Map<Integer, Long> countMap = new HashMap<>();
        for (Comment comment : allChildComments) {
            Integer floorId = comment.getFloorCommentId();
            countMap.put(floorId, countMap.getOrDefault(floorId, 0L) + 1);
        }

        // 确保所有主评论都有统计结果（没有子评论的返回0）
        Map<Integer, Long> result = new HashMap<>(mainCommentIds.size());
        for (Integer mainCommentId : mainCommentIds) {
            result.put(mainCommentId, countMap.getOrDefault(mainCommentId, 0L));
        }

        return result;
    }



    /**
     * 🔧 修复后的方法：递归获取指定评论的所有嵌套子评论（深度优先遍历）
     * 确保子评论紧跟在其父评论下方显示，保持对话连贯性
     * @param parentCommentId 父评论ID
     * @param baseRequestVO 请求参数
     * @return 按层级结构排序的所有嵌套子评论列表
     */
    private List<Comment> getAllNestedComments(Integer parentCommentId, BaseRequestVO baseRequestVO) {
        List<Comment> allComments = new ArrayList<>();

        // 查询直接子评论，按创建时间升序排列
        List<Comment> directChildren = lambdaQuery()
            .eq(Comment::getSource, baseRequestVO.getSource())
            .eq(Comment::getType, baseRequestVO.getCommentType())
            .eq(Comment::getParentCommentId, parentCommentId)
            .orderByAsc(Comment::getCreateTime)
            .list();

        log.debug("[DEBUG] 🔍 查询父评论ID:{} 的直接子评论: {}条", parentCommentId, directChildren.size());

        // 🔧 添加详细的直接子评论信息
        for (int i = 0; i < directChildren.size(); i++) {
            Comment child = directChildren.get(i);
            log.debug("[DEBUG]   直接子评论[{}]: ID={}, 用户ID={}, 内容长度={}, 创建时间={}",
                     i + 1, child.getId(), child.getUserId(),
                     child.getCommentContent() != null ? child.getCommentContent().length() : 0,
                     child.getCreateTime());
        }

        // 🔧 关键修复：使用深度优先遍历，确保每个子评论的回复紧跟在其后面
        for (Comment child : directChildren) {
            // 先添加当前子评论
            allComments.add(child);
            log.debug("[DEBUG] ➕ 添加子评论ID:{} 到结果列表，当前位置:{}", child.getId(), allComments.size());

            // 然后递归获取该子评论的所有嵌套子评论，并立即添加到结果列表
            log.debug("[DEBUG] 🔄 开始递归查询评论ID:{} 的子评论", child.getId());
            List<Comment> nestedChildren = getAllNestedComments(child.getId(), baseRequestVO);
            allComments.addAll(nestedChildren);
            log.debug("[DEBUG] 🔍 评论ID:{} 的嵌套子评论: {}条，累计总数: {}条",
                     child.getId(), nestedChildren.size(), allComments.size());
        }

        log.debug("[DEBUG] 📊 getAllNestedComments完成 - 父评论ID:{}, 返回总数:{}条", parentCommentId, allComments.size());
        return allComments;
    }

    /**
     * 🔧 新接口：子评论懒加载查询
     * 支持分页加载某个评论的子评论
     *
     * @param parentCommentId 父评论ID
     * @param baseRequestVO 基础请求参数（包含source、type等）
     * @param current 当前页码
     * @param size 每页大小（默认10）
     * @return 分页的子评论列表
     */
    public PoetryResult<Page<CommentVO>> listChildComments(Integer parentCommentId, BaseRequestVO baseRequestVO, Integer current, Integer size) {
        // 🔧 添加详细的参数调试日志
        log.debug("[DEBUG] 🔍 listChildComments参数验证:");
        log.debug("  - parentCommentId: {}", parentCommentId);
        log.debug("  - baseRequestVO: {}", baseRequestVO);
        log.debug("  - baseRequestVO.getSource(): {}", baseRequestVO != null ? baseRequestVO.getSource() : "baseRequestVO is null");
        log.debug("  - baseRequestVO.getCommentType(): {}", baseRequestVO != null ? baseRequestVO.getCommentType() : "baseRequestVO is null");
        log.debug("  - current: {}", current);
        log.debug("  - size: {}", size);

        // 🔧 修复：添加baseRequestVO null检查
        if (parentCommentId == null) {
            log.error("[ERROR] ❌ parentCommentId为null");
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        if (baseRequestVO == null) {
            log.error("[ERROR] ❌ baseRequestVO为null");
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        if (baseRequestVO.getSource() == null) {
            log.error("[ERROR] ❌ baseRequestVO.getSource()为null");
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        if (!StringUtils.hasText(baseRequestVO.getCommentType())) {
            log.error("[ERROR] ❌ baseRequestVO.getCommentType()为空或null: '{}'", baseRequestVO.getCommentType());
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        log.debug("[DEBUG] ✅ 参数验证通过");

        // 设置默认分页参数
        int pageNum = current != null ? current : 1;
        int pageSize = size != null ? size : 10;

        log.debug("[DEBUG] 🔍 开始查询子评论 - 父评论ID:{}, 页码:{}, 每页:{}", parentCommentId, pageNum, pageSize);

        long queryStartTime = System.currentTimeMillis();

        // 🔧 修复：查询所有嵌套子评论并平铺显示
        List<Comment> allNestedComments = getAllNestedComments(parentCommentId, baseRequestVO);

        long queryEndTime = System.currentTimeMillis();

        log.debug("[DEBUG] 📊 所有嵌套子评论查询完成 - 父评论ID:{}, 查询到{}条, 耗时:{}ms",
                 parentCommentId, allNestedComments.size(), queryEndTime - queryStartTime);

        // 🔧 修复：移除全局时间排序，保持深度优先遍历的层级结构
        // 注释掉原来的全局时间排序，因为getAllNestedComments已经实现了正确的深度优先排序
        // allNestedComments.sort((a, b) -> a.getCreateTime().compareTo(b.getCreateTime()));

        log.debug("[DEBUG] 🎯 保持深度优先排序结构，不进行全局时间排序");

        // 应用分页
        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allNestedComments.size());
        List<Comment> pagedComments = allNestedComments.subList(startIndex, endIndex);

        log.debug("[DEBUG] 📊 分页处理完成 - 总数:{}, 当前页:{}, 每页:{}, 返回:{}条",
                 allNestedComments.size(), pageNum, pageSize, pagedComments.size());

        // 🔧 添加详细的数据转换调试
        log.debug("[DEBUG] 🔄 开始构建CommentVO对象，待转换评论数: {}", pagedComments.size());

        List<CommentVO> childCommentVOs = new ArrayList<>();
        for (int i = 0; i < pagedComments.size(); i++) {
            Comment comment = pagedComments.get(i);
            log.debug("[DEBUG] 🔄 转换第{}个评论 - ID:{}, 用户:{}, 内容长度:{}",
                     i + 1, comment.getId(), comment.getUserId(),
                     comment.getCommentContent() != null ? comment.getCommentContent().length() : 0);

            CommentVO commentVO = buildCommentVO(comment);
            if (commentVO != null) {
                childCommentVOs.add(commentVO);
                log.debug("[DEBUG] ✅ 第{}个CommentVO构建成功 - ID:{}, 用户名:{}",
                         i + 1, commentVO.getId(), commentVO.getUsername());
            } else {
                log.error("[ERROR] ❌ 第{}个CommentVO构建失败 - 原始评论ID:{}", i + 1, comment.getId());
            }
        }

        // 创建返回的分页对象
        Page<CommentVO> result = new Page<>(pageNum, pageSize);
        result.setRecords(childCommentVOs);
        result.setTotal(allNestedComments.size());

        log.debug("[DEBUG] 📦 最终结果构建:");
        log.debug("  - 分页对象 - 当前页:{}, 每页大小:{}, 总记录数:{}", result.getCurrent(), result.getSize(), result.getTotal());
        log.debug("  - 实际返回记录数: {}", result.getRecords().size());
        log.debug("  - 记录详情:");
        for (int i = 0; i < result.getRecords().size(); i++) {
            CommentVO vo = result.getRecords().get(i);
            log.debug("    [{}] ID:{}, 用户名:{}, 内容:{}",
                     i + 1, vo.getId(), vo.getUsername(),
                     vo.getCommentContent() != null ? vo.getCommentContent().substring(0, Math.min(50, vo.getCommentContent().length())) + "..." : "null");
        }

        PoetryResult<Page<CommentVO>> poetryResult = PoetryResult.success(result);
        log.debug("[DEBUG] 🎯 PoetryResult构建完成 - code:{}, message:{}, data不为null:{}",
                 poetryResult.getCode(), poetryResult.getMessage(), poetryResult.getData() != null);

        return poetryResult;
    }

    /**
     * 构建CommentVO并递归加载其直接回复
     * @param c 评论实体
     * @param baseRequestVO 请求参数
     * @param maxDepth 最大递归深度，防止无限递归
     * @return CommentVO
     */
    private CommentVO buildCommentVOWithChildren(Comment c, BaseRequestVO baseRequestVO, int maxDepth) {
        CommentVO commentVO = buildCommentVO(c);

        // 如果达到最大深度，只计算回复统计，不加载具体内容
        if (maxDepth <= 0) {
            // 仍然需要计算回复数量用于显示统计
            Long childCount = lambdaQuery()
                .eq(Comment::getSource, baseRequestVO.getSource())
                .eq(Comment::getType, baseRequestVO.getCommentType())
                .eq(Comment::getParentCommentId, c.getId())
                .count();

            Page<CommentVO> emptyPage = new Page<>(1, 5);
            emptyPage.setRecords(new ArrayList<>());
            emptyPage.setTotal(childCount);  // 设置正确的统计数量
            commentVO.setChildComments(emptyPage);
            return commentVO;
        }

        Page<Comment> childPage = new Page<>(1, 100); // 增加到100条，实际使用list()查询所有

        List<Comment> allChildComments = lambdaQuery()
            .eq(Comment::getSource, baseRequestVO.getSource())
            .eq(Comment::getType, baseRequestVO.getCommentType())
            .eq(Comment::getParentCommentId, c.getId())  // 查询直接回复
            .orderByAsc(Comment::getCreateTime)
            .list();

        // 创建一个包含所有子评论的分页对象，用于保持接口兼容性
        Page<Comment> childResultPage = new Page<>(1, allChildComments.size());
        childResultPage.setRecords(allChildComments);
        childResultPage.setTotal(allChildComments.size());

        List<Comment> childComments = childResultPage.getRecords();
        if (childComments != null && !childComments.isEmpty()) {
            // 递归构建子评论的VO，深度减1
            List<CommentVO> childCommentVOs = childComments.stream()
                .map(cc -> buildCommentVOWithChildren(cc, baseRequestVO, maxDepth - 1))
                .collect(Collectors.toList());

            Page<CommentVO> childCommentsPage = new Page<>(childPage.getCurrent(), childPage.getSize());
            childCommentsPage.setRecords(childCommentVOs);
            childCommentsPage.setTotal(childResultPage.getTotal());
            commentVO.setChildComments(childCommentsPage);
        } else {
            // 如果没有子评论，创建空的分页对象
            Page<CommentVO> childCommentsPage = new Page<>(1, 5);
            childCommentsPage.setRecords(new ArrayList<>());
            childCommentsPage.setTotal(0);
            commentVO.setChildComments(childCommentsPage);
        }

        return commentVO;
    }
}
