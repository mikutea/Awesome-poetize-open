package com.ld.poetry.utils;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.*;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.vo.FamilyVO;
import org.apache.commons.io.IOUtils;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;


@Component
public class CommonQuery {
    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private FamilyMapper familyMapper;

    @Autowired
    private CacheService cacheService;

    private Searcher searcher;

    @PostConstruct
    public void init() {
        try {
            searcher = Searcher.newWithBuffer(IOUtils.toByteArray(new ClassPathResource("ip2region.xdb").getInputStream()));
        } catch (Exception e) {
        }
    }

    public void saveHistory(String ip) {
        // 过滤无效IP，避免记录Docker内部IP和无效地址
        if (ip == null || ip.isEmpty() || "unknown".equals(ip) || isInvalidIP(ip)) {
            return;
        }
        
        Integer userId = PoetryUtil.getUserId();
        String ipUser = ip + (userId != null ? "_" + userId.toString() : "");

        @SuppressWarnings("unchecked")
        CopyOnWriteArraySet<String> ipHistory = (CopyOnWriteArraySet<String>) cacheService.getCachedIpHistory();
        if (ipHistory != null && !ipHistory.contains(ipUser)) {
            synchronized (ipUser.intern()) {
                if (!ipHistory.contains(ipUser)) {
                    ipHistory.add(ipUser);
                    HistoryInfo historyInfo = new HistoryInfo();
                    historyInfo.setIp(ip);
                    historyInfo.setUserId(userId);
                    if (searcher != null) {
                        try {
                            String search = searcher.search(ip);
                            String[] region = search.split("\\|");
                            if (!"0".equals(region[0])) {
                                historyInfo.setNation(region[0]);
                            }
                            if (!"0".equals(region[2])) {
                                historyInfo.setProvince(region[2]);
                            }
                            if (!"0".equals(region[3])) {
                                historyInfo.setCity(region[3]);
                            }
                        } catch (Exception e) {
                            // IP解析失败时记录日志，但仍然保存IP记录
                            System.err.println("IP地理位置解析失败: " + ip + ", 错误: " + e.getMessage());
                        }
                    }
                    historyInfoMapper.insert(historyInfo);
                }
            }
        }
    }
    
    /**
     * 判断是否为无效IP地址
     */
    private boolean isInvalidIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        
        // 本地回环地址
        if (ip.equals("127.0.0.1") || ip.equals("localhost") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return true;
        }
        
        // Docker内部网络地址
        if (ip.startsWith("172.") || ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        
        // 其他无效IP
        if (ip.equals("unknown") || ip.equals("0.0.0.0") || ip.equals("null")) {
            return true;
        }
        
        return false;
    }

    public User getUser(Integer userId) {
        // 使用Redis缓存替换PoetryCache
        User user = cacheService.getCachedUser(userId);
        if (user != null) {
            return user;
        }
        User u = userService.getById(userId);
        if (u != null) {
            cacheService.cacheUser(u);
            return u;
        }
        return null;
    }

    public List<User> getAdmire() {
        // 使用Redis缓存替换PoetryCache
        String cacheKey = CacheConstants.CACHE_PREFIX + "admire:list";
        @SuppressWarnings("unchecked")
        List<User> admire = (List<User>) cacheService.get(cacheKey);
        if (admire != null) {
            return admire;
        }

        synchronized (CommonConst.ADMIRE.intern()) {
            // 双重检查锁定
            @SuppressWarnings("unchecked")
            List<User> cachedAdmire = (List<User>) cacheService.get(cacheKey);
            if (cachedAdmire != null) {
                return cachedAdmire;
            } else {
                List<User> users = userService.lambdaQuery().select(User::getId, User::getUsername, User::getAdmire, User::getAvatar).isNotNull(User::getAdmire).list();

                cacheService.set(cacheKey, users, CacheConstants.LONG_EXPIRE_TIME);

                return users;
            }
        }
    }

    public List<FamilyVO> getFamilyList() {
        // 使用Redis缓存替换PoetryCache
        String cacheKey = CacheConstants.CACHE_PREFIX + "family:list";
        @SuppressWarnings("unchecked")
        List<FamilyVO> familyVOList = (List<FamilyVO>) cacheService.get(cacheKey);
        if (familyVOList != null) {
            return familyVOList;
        }

        synchronized (CommonConst.FAMILY_LIST.intern()) {
            // 双重检查锁定
            @SuppressWarnings("unchecked")
            List<FamilyVO> cachedFamilyVOList = (List<FamilyVO>) cacheService.get(cacheKey);
            if (cachedFamilyVOList != null) {
                return cachedFamilyVOList;
            } else {
                LambdaQueryChainWrapper<Family> queryChainWrapper = new LambdaQueryChainWrapper<>(familyMapper);
                List<Family> familyList = queryChainWrapper.eq(Family::getStatus, Boolean.TRUE).list();
                if (!CollectionUtils.isEmpty(familyList)) {
                    familyVOList = familyList.stream().map(family -> {
                        FamilyVO familyVO = new FamilyVO();
                        BeanUtils.copyProperties(family, familyVO);
                        return familyVO;
                    }).collect(Collectors.toList());
                } else {
                    familyVOList = new ArrayList<>();
                }

                cacheService.set(cacheKey, familyVOList, CacheConstants.LONG_EXPIRE_TIME);
                return familyVOList;
            }
        }
    }

    public Integer getCommentCount(Integer source, String type) {
        // 使用Redis缓存替换PoetryCache
        Long cachedCount = cacheService.getCachedCommentCount(source, type);
        if (cachedCount != null) {
            return cachedCount.intValue();
        }

        LambdaQueryChainWrapper<Comment> wrapper = new LambdaQueryChainWrapper<>(commentMapper);
        Long c = wrapper.eq(Comment::getSource, source).eq(Comment::getType, type).count();
        Integer result = c.intValue();

        // 缓存评论数量
        cacheService.cacheCommentCount(source, type, c);
        return result;
    }

    public List<Integer> getUserArticleIds(Integer userId) {
        // 使用Redis缓存替换PoetryCache
        String cacheKey = CacheConstants.CACHE_PREFIX + "user:article:list:" + userId;
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) cacheService.get(cacheKey);
        if (ids != null) {
            return ids;
        }

        synchronized ((CommonConst.USER_ARTICLE_LIST + userId.toString()).intern()) {
            // 双重检查锁定
            @SuppressWarnings("unchecked")
            List<Integer> cachedIds = (List<Integer>) cacheService.get(cacheKey);
            if (cachedIds != null) {
                return cachedIds;
            } else {
                LambdaQueryChainWrapper<Article> wrapper = new LambdaQueryChainWrapper<>(articleMapper);
                List<Article> articles = wrapper.eq(Article::getUserId, userId).select(Article::getId).list();
                List<Integer> collect = articles.stream().map(Article::getId).collect(Collectors.toList());
                cacheService.set(cacheKey, collect, CacheConstants.LONG_EXPIRE_TIME);
                return collect;
            }
        }
    }

    public List<List<Integer>> getArticleIds(String searchText) {
        // 限制文章搜索关键词长度，避免过长搜索导致性能问题
        if (StringUtils.hasText(searchText) && searchText.length() > 50) {
            searchText = searchText.substring(0, 50);
        }
        
        // 使用Redis缓存替换PoetryCache
        String cacheKey = CacheConstants.CACHE_PREFIX + "search:article:" + (searchText != null ? searchText.hashCode() : "empty");

        @SuppressWarnings("unchecked")
        List<List<Integer>> cachedIds = (List<List<Integer>>) cacheService.get(cacheKey);
        if (cachedIds != null) {
            return cachedIds;
        }
        
        // 直接在数据库层面执行搜索，避免加载所有文章内容到内存
        List<List<Integer>> ids = new ArrayList<>();
        List<Integer> titleIds = new ArrayList<>();
        List<Integer> contentIds = new ArrayList<>();

        if (StringUtils.hasText(searchText)) {
            // 搜索标题包含关键词的文章ID
            LambdaQueryChainWrapper<Article> titleWrapper = new LambdaQueryChainWrapper<>(articleMapper);
            titleIds = titleWrapper
                    .select(Article::getId)
                    .eq(Article::getDeleted, false)
                    .like(Article::getArticleTitle, searchText)
                    .last("LIMIT 100") // 限制结果数量
                    .list()
                    .stream()
                    .map(Article::getId)
                    .collect(Collectors.toList());
            
            // 搜索内容包含关键词的文章ID
            LambdaQueryChainWrapper<Article> contentWrapper = new LambdaQueryChainWrapper<>(articleMapper);
            contentIds = contentWrapper
                    .select(Article::getId)
                    .eq(Article::getDeleted, false)
                    .like(Article::getArticleContent, searchText)
                    .last("LIMIT 100") // 限制结果数量
                    .list()
                    .stream()
                    .map(Article::getId)
                    .collect(Collectors.toList());
        }

        ids.add(titleIds);
        ids.add(contentIds);

        // 缓存搜索结果10分钟
        cacheService.set(cacheKey, ids, 600);

        return ids;
    }

    public List<Sort> getSortInfo() {
        // 使用Redis缓存替换PoetryCache
        @SuppressWarnings("unchecked")
        List<Sort> sortInfo = (List<Sort>) cacheService.getCachedSortList();
        if (sortInfo != null) {
            return sortInfo;
        }

        synchronized (CommonConst.SORT_INFO.intern()) {
            // 双重检查锁定
            @SuppressWarnings("unchecked")
            List<Sort> cachedSortInfo = (List<Sort>) cacheService.getCachedSortList();
            if (cachedSortInfo == null) {
                List<Sort> sorts = new LambdaQueryChainWrapper<>(sortMapper).list();
                if (!CollectionUtils.isEmpty(sorts)) {
                    sorts.forEach(sort -> {
                        LambdaQueryChainWrapper<Article> sortWrapper = new LambdaQueryChainWrapper<>(articleMapper);
                        Long countOfSort = sortWrapper
                            .eq(Article::getSortId, sort.getId())
                            .eq(Article::getDeleted, false)
                            .count();
                        sort.setCountOfSort(countOfSort.intValue());

                        LambdaQueryChainWrapper<Label> wrapper = new LambdaQueryChainWrapper<>(labelMapper);
                        List<Label> labels = wrapper.eq(Label::getSortId, sort.getId()).list();
                        if (!CollectionUtils.isEmpty(labels)) {
                            labels.forEach(label -> {
                                LambdaQueryChainWrapper<Article> labelWrapper = new LambdaQueryChainWrapper<>(articleMapper);
                                Long countOfLabel = labelWrapper
                                    .eq(Article::getLabelId, label.getId())
                                    .eq(Article::getDeleted, false)
                                    .count();
                                label.setCountOfLabel(countOfLabel.intValue());
                            });
                            sort.setLabels(labels);
                        }
                    });
                }
                cacheService.cacheSortList(sorts);
                return sorts;
            } else {
                return cachedSortInfo;
            }
        }
    }
}
