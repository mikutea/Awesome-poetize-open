package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.*;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.cache.PoetryCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 网站信息表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@Slf4j
@SuppressWarnings("unchecked")
@RestController
@RequestMapping("/webInfo")
public class WebInfoController {

    @Value("${store.type}")
    private String defaultType;

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private PrerenderClient prerenderClient;

    @Autowired
    private RestTemplate restTemplate;

    // API配置的缓存键
    private static final String API_CONFIG_CACHE_KEY = "API_CONFIG";


    /**
     * 更新网站信息
     */
    @LoginCheck(0)
    @PostMapping("/updateWebInfo")
    public PoetryResult<WebInfo> updateWebInfo(@RequestBody WebInfo webInfo) {
        webInfoService.updateById(webInfo);

        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
        List<WebInfo> list = wrapper.list();
        if (!CollectionUtils.isEmpty(list)) {
            list.get(0).setDefaultStoreType(defaultType);
            PoetryCache.put(CommonConst.WEB_INFO, list.get(0));
            
            // 网站信息更新时，重新渲染首页和百宝箱页面
            try {
                prerenderClient.renderMainPages();
            } catch (Exception e) {
                // 预渲染失败不影响主流程，只记录日志
                // 日志已在PrerenderClient中记录
            }
        }
        return PoetryResult.success();
    }


    /**
     * 获取网站信息
     */
    @GetMapping("/getWebInfo")
    public PoetryResult<WebInfo> getWebInfo() {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo != null) {
            WebInfo result = new WebInfo();
            BeanUtils.copyProperties(webInfo, result);
            result.setRandomAvatar(null);
            result.setRandomName(null);
            result.setWaifuJson(null);

            webInfo.setHistoryAllCount(((Long) ((Map<String, Object>) PoetryCache.get(CommonConst.IP_HISTORY_STATISTICS)).get(CommonConst.IP_HISTORY_COUNT)).toString());
            webInfo.setHistoryDayCount(Integer.toString(((List<Map<String, Object>>) ((Map<String, Object>) PoetryCache.get(CommonConst.IP_HISTORY_STATISTICS)).get(CommonConst.IP_HISTORY_HOUR)).size()));
            return PoetryResult.success(result);
        }
        return PoetryResult.success();
    }

    /**
     * 获取网站统计信息
     */
    @LoginCheck(0)
    @GetMapping("/getHistoryInfo")
    public PoetryResult<Map<String, Object>> getHistoryInfo() {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> history = (Map<String, Object>) PoetryCache.get(CommonConst.IP_HISTORY_STATISTICS);
        List<HistoryInfo> infoList = new LambdaQueryChainWrapper<>(historyInfoMapper)
                .select(HistoryInfo::getIp, HistoryInfo::getUserId, HistoryInfo::getNation, HistoryInfo::getProvince, HistoryInfo::getCity)
                .ge(HistoryInfo::getCreateTime, LocalDateTime.now().with(LocalTime.MIN))
                .list();

        result.put(CommonConst.IP_HISTORY_PROVINCE, history.get(CommonConst.IP_HISTORY_PROVINCE));
        result.put(CommonConst.IP_HISTORY_IP, history.get(CommonConst.IP_HISTORY_IP));
        result.put(CommonConst.IP_HISTORY_COUNT, history.get(CommonConst.IP_HISTORY_COUNT));
        List<Map<String, Object>> ipHistoryCount = (List<Map<String, Object>>) history.get(CommonConst.IP_HISTORY_HOUR);
        result.put("ip_count_yest", ipHistoryCount.stream().map(m -> m.get("ip")).distinct().count());
        result.put("username_yest", ipHistoryCount.stream().map(m -> {
            Object userId = m.get("user_id");
            if (userId != null) {
                User user = commonQuery.getUser(Integer.valueOf(userId.toString()));
                if (user != null) {
                    Map<String, String> userInfo = new HashMap<>();
                    userInfo.put("avatar", user.getAvatar());
                    userInfo.put("username", user.getUsername());
                    return userInfo;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList()));
        result.put("ip_count_today", infoList.stream().map(HistoryInfo::getIp).distinct().count());
        result.put("username_today", infoList.stream().map(m -> {
            Integer userId = m.getUserId();
            if (userId != null) {
                User user = commonQuery.getUser(userId);
                if (user != null) {
                    Map<String, String> userInfo = new HashMap<>();
                    userInfo.put("avatar", user.getAvatar());
                    userInfo.put("username", user.getUsername());
                    return userInfo;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList()));

        List<Map<String, Object>> list = infoList.stream()
                .map(HistoryInfo::getProvince).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("province", entry.getKey());
                    map.put("num", entry.getValue());
                    return map;
                })
                .sorted((o1, o2) -> Long.valueOf(o2.get("num").toString()).compareTo(Long.valueOf(o1.get("num").toString())))
                .collect(Collectors.toList());

        result.put("province_today", list);

        return PoetryResult.success(result);
    }

    /**
     * 获取赞赏
     */
    @GetMapping("/getAdmire")
    public PoetryResult<List<User>> getAdmire() {
        return PoetryResult.success(commonQuery.getAdmire());
    }

    /**
     * 获取看板娘消息
     */
    @GetMapping("/getWaifuJson")
    public String getWaifuJson() {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo != null && StringUtils.hasText(webInfo.getWaifuJson())) {
            return webInfo.getWaifuJson();
        }
        return "{}";
    }

    /**
     * 清除分类信息缓存
     */
    @GetMapping("/clearSortCache")
    public PoetryResult<String> clearSortCache() {
        PoetryCache.remove(CommonConst.SORT_INFO);
        return PoetryResult.success();
    }

    /**
     * 获取API配置
     */
    @LoginCheck(0)
    @GetMapping("/getApiConfig")
    public PoetryResult<Map<String, Object>> getApiConfig() {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
                webInfo.setDefaultStoreType(defaultType);
                PoetryCache.put(CommonConst.WEB_INFO, webInfo);
            } else {
                webInfo = new WebInfo();
            }
        }
        
        Map<String, Object> apiConfig = new HashMap<>();
        apiConfig.put("enabled", webInfo.getApiEnabled() != null ? webInfo.getApiEnabled() : false);
        apiConfig.put("apiKey", webInfo.getApiKey() != null ? webInfo.getApiKey() : generateApiKey());
        
        return PoetryResult.success(apiConfig);
    }

    /**
     * 保存API配置
     */
    @LoginCheck(0)
    @PostMapping("/saveApiConfig")
    public PoetryResult<String> saveApiConfig(@RequestBody Map<String, Object> apiConfig) {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
            } else {
                return PoetryResult.fail("网站信息不存在");
            }
        }
        
        Boolean enabled = (Boolean) apiConfig.get("enabled");
        String apiKey = (String) apiConfig.get("apiKey");
        
        // 如果提交的配置不包含apiKey，生成一个新的
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = generateApiKey();
        }
        
        // 更新数据库
        WebInfo updateInfo = new WebInfo();
        updateInfo.setId(webInfo.getId());
        updateInfo.setApiEnabled(enabled);
        updateInfo.setApiKey(apiKey);
        webInfoService.updateById(updateInfo);
        
        // 更新缓存
        webInfo.setApiEnabled(enabled);
        webInfo.setApiKey(apiKey);
        PoetryCache.put(CommonConst.WEB_INFO, webInfo);
        
        return PoetryResult.success();
    }

    /**
     * 重新生成API密钥
     */
    @LoginCheck(0)
    @PostMapping("/regenerateApiKey")
    public PoetryResult<String> regenerateApiKey() {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
            } else {
                return PoetryResult.fail("网站信息不存在");
            }
        }
        
        String newApiKey = generateApiKey();
        
        // 更新数据库
        WebInfo updateInfo = new WebInfo();
        updateInfo.setId(webInfo.getId());
        updateInfo.setApiKey(newApiKey);
        webInfoService.updateById(updateInfo);
        
        // 更新缓存
        webInfo.setApiKey(newApiKey);
        PoetryCache.put(CommonConst.WEB_INFO, webInfo);
        
        return PoetryResult.success(newApiKey);
    }
    
    /**
     * 生成API密钥
     */
    private String generateApiKey() {
        return UUID.randomUUID().toString().replaceAll("-", "") + 
               UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
    }

    /**
     * 获取每日访问量统计
     * @param days 查询天数(1-365)，默认7
     */
    @LoginCheck(0)
    @GetMapping("/getDailyVisitStats")
    public PoetryResult<List<Map<String, Object>>> getDailyVisitStats(@RequestParam(value = "days", defaultValue = "7") Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        } else if (days > 365) {
            days = 365;
        }

        List<Map<String, Object>> stats = historyInfoMapper.getDailyVisitStats(days);

        if (stats == null) {
            return PoetryResult.success(Collections.emptyList());
        }

        // 计算平均 unique_visits
        double avg = stats.stream()
                .map(m -> (Number) m.get("unique_visits"))
                .filter(Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(0);
        avg = Math.round(avg * 100.0) / 100.0;

        for (Map<String, Object> m : stats) {
            m.put("avg_unique_visits", avg);
        }

        return PoetryResult.success(stats);
    }

    /**
     * 检查预渲染服务状态
     */
    @GetMapping("/checkPrerenderStatus")
    public PoetryResult<Map<String, Object>> checkPrerenderStatus() {
        try {
            prerenderClient.checkHealth();
            Map<String, Object> status = new HashMap<>();
            status.put("online", true);
            status.put("lastCheck", new Date());
            return PoetryResult.success(status);
        } catch (Exception e) {
            Map<String, Object> status = new HashMap<>();
            status.put("online", false);
            status.put("error", e.getMessage());
            return PoetryResult.success(status);
        }
    }

    /**
     * 手动触发首页预渲染
     */
    @PostMapping("/renderHomePage")
    @LoginCheck(0)
    public PoetryResult<String> renderHomePage() {
        try {
            prerenderClient.renderHomePage();
            return PoetryResult.success("首页预渲染任务已提交");
        } catch (Exception e) {
            return PoetryResult.fail("提交首页预渲染任务失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发百宝箱页面预渲染
     */
    @PostMapping("/renderFavoritePage")
    @LoginCheck(0)
    public PoetryResult<String> renderFavoritePage() {
        try {
            prerenderClient.renderFavoritePage();
            return PoetryResult.success("百宝箱页面预渲染任务已提交");
        } catch (Exception e) {
            return PoetryResult.fail("提交百宝箱页面预渲染任务失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发分类索引页面预渲染
     */
    @PostMapping("/renderSortIndexPage")
    @LoginCheck(0)
    public PoetryResult<String> renderSortIndexPage() {
        try {
            prerenderClient.renderSortIndexPage();
            return PoetryResult.success("分类索引页面预渲染任务已提交");
        } catch (Exception e) {
            return PoetryResult.fail("提交分类索引页面预渲染任务失败: " + e.getMessage());
        }
    }

    /**
     * @deprecated 使用 renderSortIndexPage() 替代
     * 手动触发默认分类页面预渲染
     */
    @PostMapping("/renderDefaultSortPage")
    @LoginCheck(0)
    @Deprecated
    public PoetryResult<String> renderDefaultSortPage() {
        try {
            prerenderClient.renderDefaultSortPage();
            return PoetryResult.success("分类索引页面预渲染任务已提交");
        } catch (Exception e) {
            return PoetryResult.fail("提交分类索引页面预渲染任务失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发分类详情页面预渲染
     */
    @PostMapping("/renderCategoryPage")
    @LoginCheck(0)
    public PoetryResult<String> renderCategoryPage(@RequestBody Map<String, Object> params) {
        try {
            Integer sortId = (Integer) params.get("sortId");
            if (sortId == null) {
                return PoetryResult.fail("分类ID不能为空");
            }
            prerenderClient.renderCategoryPage(sortId);
            return PoetryResult.success("分类详情页面预渲染任务已提交");
        } catch (Exception e) {
            return PoetryResult.fail("提交分类详情页面预渲染任务失败: " + e.getMessage());
        }
    }

    /**
     * @deprecated 使用 renderCategoryPage() 替代
     * 手动触发分类页面预渲染
     */
    @PostMapping("/renderSortPage")
    @LoginCheck(0)
    @Deprecated
    public PoetryResult<String> renderSortPage(@RequestBody Map<String, Object> params) {
        try {
            Integer sortId = (Integer) params.get("sortId");
            if (sortId == null) {
                return PoetryResult.fail("分类ID不能为空");
            }
            prerenderClient.renderSortPage(sortId);
            return PoetryResult.success("分类详情页面预渲染任务已提交");
        } catch (Exception e) {
            return PoetryResult.fail("提交分类详情页面预渲染任务失败: " + e.getMessage());
        }
    }

    /**
     * 批量渲染所有分类详情页面
     */
    @PostMapping("/renderAllCategoryPages")
    @LoginCheck(0)
    public PoetryResult<String> renderAllCategoryPages(@RequestBody Map<String, Object> params) {
        try {
            @SuppressWarnings("unchecked")
            List<Integer> sortIds = (List<Integer>) params.get("sortIds");
            if (sortIds == null || sortIds.isEmpty()) {
                return PoetryResult.fail("分类ID列表不能为空");
            }
            prerenderClient.renderAllCategoryPages(sortIds);
            return PoetryResult.success("所有分类详情页面预渲染任务已提交 (共" + sortIds.size() + "个)");
        } catch (Exception e) {
            return PoetryResult.fail("提交所有分类详情页面预渲染任务失败: " + e.getMessage());
        }
    }

    /**
     * @deprecated 使用 renderAllCategoryPages() 替代
     * 手动触发所有分类页面预渲染
     */
    @PostMapping("/renderAllSortPages")
    @LoginCheck(0)
    @Deprecated
    public PoetryResult<String> renderAllSortPages(@RequestBody Map<String, Object> params) {
        try {
            @SuppressWarnings("unchecked")
            List<Integer> sortIds = (List<Integer>) params.get("sortIds");
            if (sortIds == null || sortIds.isEmpty()) {
                return PoetryResult.fail("分类ID列表不能为空");
            }
            prerenderClient.renderAllSortPages(sortIds);
            return PoetryResult.success("所有分类详情页面预渲染任务已提交 (共" + sortIds.size() + "个)");
        } catch (Exception e) {
            return PoetryResult.fail("提交所有分类详情页面预渲染任务失败: " + e.getMessage());
        }
    }

    /**
     * 全量重建所有页面
     */
    @PostMapping("/renderAllPages")
    @LoginCheck(0)
    public PoetryResult<String> renderAllPages() {
        try {
            // 异步执行全量重建，避免超时
            new Thread(() -> {
                try {
                    // 1. 渲染首页
                    prerenderClient.renderHomePage();
                    Thread.sleep(1000);
                    
                    // 2. 渲染百宝箱页面
                    prerenderClient.renderFavoritePage();
                    Thread.sleep(1000);
                    
                    // 3. 渲染默认分类页面
                    prerenderClient.renderDefaultSortPage();
                    Thread.sleep(1000);
                    
                    // 4. 获取所有分类并渲染
                    List<Sort> sorts = new LambdaQueryChainWrapper<>(sortMapper)
                            .orderByAsc(Sort::getSortType)
                            .list();
                    
                    for (Sort sort : sorts) {
                        prerenderClient.renderSortPage(sort.getId());
                        Thread.sleep(500); // 避免并发过高
                    }
                    
                    // 5. 获取所有可见文章并渲染
                    List<Article> articles = new LambdaQueryChainWrapper<>(articleMapper)
                            .eq(Article::getViewStatus, true)
                            .orderByDesc(Article::getCreateTime)
                            .list();
                    
                    List<Integer> articleIds = articles.stream()
                            .map(Article::getId)
                            .collect(Collectors.toList());
                    
                    if (!articleIds.isEmpty()) {
                        prerenderClient.renderArticles(articleIds);
                    }
                    
                } catch (Exception e) {
                    log.error("全量重建预渲染失败", e);
                }
            }).start();
            
            return PoetryResult.success("全量重建任务已启动，将在后台执行");
        } catch (Exception e) {
            return PoetryResult.fail("启动全量重建任务失败: " + e.getMessage());
        }
    }

    /**
     * 清理分类缓存
     */
    @PostMapping("/clearSortCache")
    @LoginCheck(0)
    public PoetryResult<String> clearSortCache(@RequestBody Map<String, Object> params) {
        try {
            Integer sortId = (Integer) params.get("sortId");
            if (sortId == null) {
                return PoetryResult.fail("分类ID不能为空");
            }
            
            // 删除分类页面的预渲染文件
            prerenderClient.deleteSortPage(sortId);
            
            return PoetryResult.success("分类缓存已清理");
        } catch (Exception e) {
            return PoetryResult.fail("清理分类缓存失败: " + e.getMessage());
        }
    }

    /**
     * 获取分类列表用于预渲染管理
     */
    @GetMapping("/listSortForPrerender")
    public PoetryResult<List<Map<String, Object>>> listSortForPrerender() {
        try {
            List<Sort> sorts = new LambdaQueryChainWrapper<>(sortMapper)
                    .orderByAsc(Sort::getSortType)
                    .list();
            
            // 统计每个分类的文章数量
            List<Map<String, Object>> result = sorts.stream().map(sort -> {
                Map<String, Object> sortInfo = new HashMap<>();
                sortInfo.put("id", sort.getId());
                sortInfo.put("sortName", sort.getSortName());
                sortInfo.put("sortDescription", sort.getSortDescription());
                
                // 统计该分类下的文章数量
                Long articleCount = new LambdaQueryChainWrapper<>(articleMapper)
                        .eq(Article::getSortId, sort.getId())
                        .eq(Article::getViewStatus, true)
                        .count();
                sortInfo.put("countOfSort", articleCount);
                
                return sortInfo;
            }).collect(Collectors.toList());
            
            return PoetryResult.success(result);
        } catch (Exception e) {
            return PoetryResult.fail("获取分类列表失败: " + e.getMessage());
        }
    }

    // ===== 新增：预渲染监控和日志管理API =====

    /**
     * 创建带有内部服务标识的HTTP头
     */
    private HttpHeaders createInternalServiceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "poetize-java");
        headers.set("User-Agent", "poetize-java/1.0.0");
        return headers;
    }

    /**
     * 获取预渲染服务详细状态
     */
    @GetMapping("/prerender/status")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPrerenderStatus() {
        try {
            String url = "http://poetize-prerender:4000/status";
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("获取预渲染服务状态失败", e);
            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("status", "error");
            errorStatus.put("message", e.getMessage());
            errorStatus.put("online", false);
            return PoetryResult.success(errorStatus);
        }
    }

    /**
     * 获取预渲染服务监控仪表板数据
     */
    @GetMapping("/prerender/dashboard")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPrerenderDashboard() {
        try {
            String url = "http://poetize-prerender:4000/monitor/dashboard";
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("获取预渲染仪表板数据失败", e);
            return PoetryResult.fail("获取预渲染仪表板数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取预渲染实时日志
     */
    @GetMapping("/prerender/logs")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPrerenderLogs(
            @RequestParam(value = "limit", defaultValue = "100") Integer limit,
            @RequestParam(value = "level", required = false) String level) {
        try {
            String url = "http://poetize-prerender:4000/logs?limit=" + limit;
            if (level != null && !level.trim().isEmpty()) {
                url += "&level=" + level;
            }
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("获取预渲染日志失败", e);
            return PoetryResult.fail("获取预渲染日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取预渲染错误日志
     */
    @GetMapping("/prerender/errors")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPrerenderErrors(@RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        try {
            String url = "http://poetize-prerender:4000/errors?limit=" + limit;
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("获取预渲染错误日志失败", e);
            return PoetryResult.fail("获取预渲染错误日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取预渲染日志文件列表
     */
    @GetMapping("/prerender/logs/files")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPrerenderLogFiles() {
        try {
            String url = "http://poetize-prerender:4000/logs/files";
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("获取预渲染日志文件列表失败", e);
            return PoetryResult.fail("获取预渲染日志文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 读取特定预渲染日志文件
     */
    @GetMapping("/prerender/logs/files/{filename}")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPrerenderLogFile(
            @PathVariable("filename") String filename,
            @RequestParam(value = "lines", defaultValue = "1000") Integer lines,
            @RequestParam(value = "level", required = false) String level) {
        try {
            String url = "http://poetize-prerender:4000/logs/files/" + filename + "?lines=" + lines;
            if (level != null && !level.trim().isEmpty()) {
                url += "&level=" + level;
            }
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("读取预渲染日志文件失败", e);
            return PoetryResult.fail("读取预渲染日志文件失败: " + e.getMessage());
        }
    }

    /**
     * 下载预渲染日志文件
     */
    @GetMapping("/prerender/logs/download/{filename}")
    @LoginCheck(0)
    public ResponseEntity<byte[]> downloadPrerenderLogFile(@PathVariable("filename") String filename) {
        try {
            String url = "http://poetize-prerender:4000/logs/download/" + filename;
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            ResponseEntity<byte[]> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, byte[].class);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
            
            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("下载预渲染日志文件失败", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 清理预渲染服务数据
     */
    @PostMapping("/prerender/cleanup")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> cleanupPrerenderService(@RequestBody(required = false) Map<String, Object> params) {
        try {
            String url = "http://poetize-prerender:4000/cleanup";
            
            HttpHeaders headers = createInternalServiceHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(params != null ? params : new HashMap<>(), headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("清理预渲染服务失败", e);
            return PoetryResult.fail("清理预渲染服务失败: " + e.getMessage());
        }
    }

    /**
     * 获取预渲染服务健康状态详细信息
     */
    @GetMapping("/prerender/health")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPrerenderHealth() {
        try {
            String url = "http://poetize-prerender:4000/health";
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("获取预渲染健康状态失败", e);
            Map<String, Object> errorHealth = new HashMap<>();
            errorHealth.put("status", "unhealthy");
            errorHealth.put("error", e.getMessage());
            errorHealth.put("timestamp", new Date());
            return PoetryResult.success(errorHealth);
        }
    }

    /**
     * 获取预渲染日志磁盘使用情况
     */
    @GetMapping("/prerender/logs/usage")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPrerenderLogUsage() {
        try {
            String url = "http://poetize-prerender:4000/logs/usage";
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("获取预渲染日志磁盘使用情况失败", e);
            return PoetryResult.fail("获取预渲染日志磁盘使用情况失败: " + e.getMessage());
        }
    }

    /**
     * 手动清理预渲染日志文件
     */
    @PostMapping("/prerender/logs/cleanup")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> cleanupPrerenderLogs(@RequestBody(required = false) Map<String, Object> params) {
        try {
            String url = "http://poetize-prerender:4000/logs/cleanup";
            
            HttpHeaders headers = createInternalServiceHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(params != null ? params : new HashMap<>(), headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            return PoetryResult.success(response.getBody());
        } catch (Exception e) {
            log.error("清理预渲染日志失败", e);
            return PoetryResult.fail("清理预渲染日志失败: " + e.getMessage());
        }
    }
}

