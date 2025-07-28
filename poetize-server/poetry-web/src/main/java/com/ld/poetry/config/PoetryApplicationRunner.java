package com.ld.poetry.config;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.dao.HistoryInfoMapper;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.entity.*;
import com.ld.poetry.im.websocket.TioUtil;
import com.ld.poetry.im.websocket.TioWebsocketStarter;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.FamilyService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.enums.PoetryEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PoetryApplicationRunner implements ApplicationRunner {

    @Value("${store.type}")
    private String defaultType;

    @Autowired
    private WebInfoMapper webInfoMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private CacheService cacheService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 初始化网站信息缓存
        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
        List<WebInfo> list = wrapper.list();
        if (!CollectionUtils.isEmpty(list)) {
            WebInfo webInfo = list.get(0);
            webInfo.setDefaultStoreType(defaultType);

            // 确保启用看板娘字段有默认值
            if (webInfo.getEnableWaifu() == null) {
                webInfo.setEnableWaifu(false);
            }

            // 确保status字段有默认值
            if (webInfo.getStatus() == null) {
                webInfo.setStatus(true);
                log.info("WebInfo status字段为null，设置为默认值true");
            }

            // 缓存网站信息到Redis（永久缓存）
            cacheService.cacheWebInfo(webInfo);

            log.info("网站基本信息已加载到Redis缓存(永久) - WebName: {}, EnableWaifu: {}, Status: {}",
                    webInfo.getWebName(), webInfo.getEnableWaifu(), webInfo.getStatus());
        } else {
            log.warn("未找到网站基本信息，请检查数据库");
        }

        // 初始化管理员用户缓存
        User admin = userService.lambdaQuery().eq(User::getUserType, PoetryEnum.USER_TYPE_ADMIN.getCode()).one();
        if (admin != null) {
            cacheService.cacheAdminUser(admin);
            log.info("管理员用户信息已加载到Redis缓存(永久) - Username: {}, ID: {}, Email: {}", 
                admin.getUsername(), admin.getId(), admin.getEmail());
        } else {
            log.error("未找到管理员用户，请检查数据库！应用可能无法正常工作");
        }

        // 初始化管理员家庭信息缓存
        if (admin != null) {
            Family family = familyService.lambdaQuery().eq(Family::getUserId, admin.getId()).one();
            if (family != null) {
                cacheService.cacheAdminFamily(family);
                log.info("管理员家庭信息已加载到缓存");
            }
        }

        // 初始化历史访问缓存
        List<HistoryInfo> infoList = new LambdaQueryChainWrapper<>(historyInfoMapper)
                .select(HistoryInfo::getIp, HistoryInfo::getUserId)
                .ge(HistoryInfo::getCreateTime, LocalDateTime.now().with(LocalTime.MIN))
                .list();

        cacheService.cacheIpHistory(new CopyOnWriteArraySet<>(infoList.stream().map(info -> info.getIp() + (info.getUserId() != null ? "_" + info.getUserId().toString() : "")).collect(Collectors.toList())));

        // 初始化访问统计缓存
        Map<String, Object> history = new HashMap<>();
        history.put(CommonConst.IP_HISTORY_PROVINCE, historyInfoMapper.getHistoryByProvince());
        history.put(CommonConst.IP_HISTORY_IP, historyInfoMapper.getHistoryByIp());
        history.put(CommonConst.IP_HISTORY_HOUR, historyInfoMapper.getHistoryBy24Hour());
        history.put(CommonConst.IP_HISTORY_COUNT, historyInfoMapper.getHistoryCount());
        cacheService.cacheIpHistoryStatistics(history);

        // 初始化Tio
        TioUtil.buildTio();
        TioWebsocketStarter websocketStarter = TioUtil.getTio();
        if (websocketStarter != null) {
            websocketStarter.start();
        }
    }
}
