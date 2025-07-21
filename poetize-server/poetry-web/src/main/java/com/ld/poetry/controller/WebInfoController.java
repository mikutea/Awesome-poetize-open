package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.*;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import com.ld.poetry.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;


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

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private PrerenderClient prerenderClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ThirdPartyOauthConfigService thirdPartyOauthConfigService;

    @Autowired
    private CacheService cacheService;

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
            // 使用Redis缓存替换PoetryCache
            cacheService.cacheWebInfo(list.get(0));
            
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
    // 静态缓存，避免频繁计算访问量
    private static WebInfo cachedPublicWebInfo = null;
    private static long lastUpdateTime = 0;
    private static final long CACHE_TTL = 10000; // 10秒缓存生效期

    @GetMapping("/getWebInfo")
    public PoetryResult<WebInfo> getWebInfo() {
        // 检查本地缓存是否有效（10秒内）
        long currentTime = System.currentTimeMillis();
        if (cachedPublicWebInfo != null && (currentTime - lastUpdateTime) < CACHE_TTL) {
            return PoetryResult.success(cachedPublicWebInfo);
        }
        
        // 缓存过期，重新构建
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo != null) {
            WebInfo result = new WebInfo();
            BeanUtils.copyProperties(webInfo, result);
            result.setRandomAvatar(null);
            result.setRandomName(null);
            result.setWaifuJson(null);

            try {
                Map<String, Object> historyStats = (Map<String, Object>) cacheService.getCachedIpHistoryStatistics();
                if (historyStats != null) {
                    // 获取访问统计
                    Long historyCount = (Long) historyStats.get(CommonConst.IP_HISTORY_COUNT);
                    List<Map<String, Object>> hourStats = (List<Map<String, Object>>) historyStats.get(CommonConst.IP_HISTORY_HOUR);
                    
                    if (historyCount != null) {
                        result.setHistoryAllCount(historyCount.toString());
                    }
                    
                    if (hourStats != null) {
                        result.setHistoryDayCount(Integer.toString(hourStats.size()));
                    }
                }
            } catch (Exception e) {
                // 捕获可能的类型转换异常，避免影响正常响应
                log.warn("获取访问统计时出错", e);
            }
            
            // 更新本地缓存
            cachedPublicWebInfo = result;
            lastUpdateTime = currentTime;
            
            return PoetryResult.success(result);
        }
        return PoetryResult.success();
    }

    /**
     * 获取用户IP地址 - 用于403页面显示
     */
    @GetMapping("/getUserIP")
    public PoetryResult<Map<String, Object>> getUserIP() {
        Map<String, Object> result = new HashMap<>();
        String clientIP = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
        result.put("ip", clientIP);
        result.put("timestamp", System.currentTimeMillis());
        return PoetryResult.success(result);
    }

    @LoginCheck(0)
    @PostMapping("/updateThirdLoginConfig")
    public PoetryResult<Object> updateThirdLoginConfig(@RequestBody Map<String, Object> config) {
        try {
            log.info("更新第三方登录配置: {}", config);

            // 直接使用数据库服务更新配置
            PoetryResult<Boolean> result = thirdPartyOauthConfigService.updateThirdLoginConfig(config);

            if (result.isSuccess()) {
                log.info("第三方登录配置更新成功");
                return PoetryResult.success("配置更新成功");
            } else {
                log.warn("第三方登录配置更新失败: {}", result.getMessage());
                return PoetryResult.fail(result.getMessage());
            }
        } catch (Exception e) {
            log.error("第三方登录配置更新失败", e);
            return PoetryResult.fail("第三方登录配置更新失败: " + e.getMessage());
        }
    }

    /**
     * 获取网站统计信息
     */
    @LoginCheck(0)
    @GetMapping("/getHistoryInfo")
    public PoetryResult<Map<String, Object>> getHistoryInfo() {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> history = (Map<String, Object>) cacheService.getCachedIpHistoryStatistics();
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
        WebInfo webInfo = cacheService.getCachedWebInfo();
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
        cacheService.evictSortList();
        return PoetryResult.success();
    }

    /**
     * 获取API配置
     */
    @LoginCheck(0)
    @GetMapping("/getApiConfig")
    public PoetryResult<Map<String, Object>> getApiConfig() {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
                cacheService.cacheWebInfo(webInfo);
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
        WebInfo webInfo = cacheService.getCachedWebInfo();
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
        cacheService.cacheWebInfo(webInfo);
        
        return PoetryResult.success();
    }

    /**
     * 重新生成API密钥
     */
    @LoginCheck(0)
    @PostMapping("/regenerateApiKey")
    public PoetryResult<String> regenerateApiKey() {
        WebInfo webInfo = cacheService.getCachedWebInfo();
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
        cacheService.cacheWebInfo(webInfo);
        
        return PoetryResult.success(newApiKey);
    }

    /**
     * 获取分类信息 - 用于预渲染服务
     * 此接口专门为prerender-worker提供分类列表数据
     */
    @GetMapping("/listSortForPrerender")
    public PoetryResult<List<Sort>> listSortForPrerender() {
        try {
            // 获取所有分类信息，包含标签
            List<Sort> sortList = new LambdaQueryChainWrapper<>(sortMapper)
                    .orderByAsc(Sort::getSortType)
                    .orderByAsc(Sort::getPriority)
                    .list();
            
            log.debug("预渲染服务获取分类列表，共{}个分类", sortList.size());
            return PoetryResult.success(sortList);
        } catch (Exception e) {
            log.error("获取预渲染分类列表失败", e);
            return PoetryResult.fail("获取分类列表失败");
        }
    }

    /**
     * 获取分类详细信息 - 用于预渲染服务
     * @param sortId 分类ID
     */
    @GetMapping("/getSortDetailForPrerender")
    public PoetryResult<Sort> getSortDetailForPrerender(@RequestParam Integer sortId) {
        if (sortId == null) {
            return PoetryResult.fail("分类ID不能为空");
        }
        
        try {
            // 获取分类基本信息
            Sort sort = sortMapper.selectById(sortId);
            if (sort == null) {
                return PoetryResult.fail("分类不存在");
            }
            
            // 获取该分类下的标签信息
            LambdaQueryChainWrapper<Label> labelWrapper = new LambdaQueryChainWrapper<>(labelMapper);
            List<Label> labels = labelWrapper.eq(Label::getSortId, sortId).list();
            sort.setLabels(labels);
            
            log.debug("预渲染服务获取分类详情，分类ID: {}, 标签数: {}", sortId, labels != null ? labels.size() : 0);
            return PoetryResult.success(sort);
        } catch (Exception e) {
            log.error("获取预渲染分类详情失败，分类ID: {}", sortId, e);
            return PoetryResult.fail("获取分类详情失败");
        }
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

    @LoginCheck(0)
    @GetMapping("/getThirdLoginConfig")
    public PoetryResult<Object> getThirdLoginConfig() {
        try {
            log.info("获取第三方登录配置");

            // 直接从数据库获取配置
            PoetryResult<Map<String, Object>> result = thirdPartyOauthConfigService.getThirdLoginConfig();

            if (result.isSuccess()) {
                log.info("第三方登录配置获取成功");
                return PoetryResult.success(result.getData());
            } else {
                log.warn("第三方登录配置获取失败: {}", result.getMessage());
                return PoetryResult.fail(result.getMessage());
            }
        } catch (Exception e) {
            log.error("获取第三方登录配置失败", e);
            return PoetryResult.fail("获取第三方登录配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取第三方登录状态（轻量级接口，用于前端状态检查）
     */
    @GetMapping("/getThirdLoginStatus")
    public PoetryResult<Object> getThirdLoginStatus(@RequestParam(required = false) String provider) {
        try {
            log.debug("获取第三方登录状态，平台: {}", provider);

            // 获取激活的配置（全局启用且平台启用）
            List<ThirdPartyOauthConfig> activeConfigs = thirdPartyOauthConfigService.getActiveConfigs();

            // 构建状态响应
            Map<String, Object> status = new HashMap<>();
            boolean globalEnabled = !activeConfigs.isEmpty();
            status.put("enable", globalEnabled);

            // 如果指定了平台，检查该平台状态
            if (provider != null && !provider.trim().isEmpty()) {
                boolean platformEnabled = activeConfigs.stream()
                    .anyMatch(config -> provider.equals(config.getPlatformType()));
                status.put(provider, Map.of("enabled", platformEnabled));
            } else {
                // 返回所有平台状态
                for (ThirdPartyOauthConfig config : activeConfigs) {
                    status.put(config.getPlatformType(), Map.of("enabled", true));
                }
            }

            return PoetryResult.success(status);
        } catch (Exception e) {
            log.error("获取第三方登录状态失败", e);
            return PoetryResult.fail("获取第三方登录状态失败: " + e.getMessage());
        }
    }

}

