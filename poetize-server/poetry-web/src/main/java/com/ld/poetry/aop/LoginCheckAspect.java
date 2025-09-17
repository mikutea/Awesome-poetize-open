package com.ld.poetry.aop;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.handle.PoetryLoginException;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;


@Aspect
@Component
@Order(0)
@Slf4j
public class LoginCheckAspect {

    @Autowired
    private CacheService cacheService;

    @Around("@annotation(loginCheck)")
    public Object around(ProceedingJoinPoint joinPoint, LoginCheck loginCheck) throws Throwable {
        // 检查是否来自内部服务的请求
        HttpServletRequest request = PoetryUtil.getRequest();
        String adminFlag = request.getHeader("X-Admin-Request");
        String internalService = request.getHeader("X-Internal-Service");
        String clientIp = PoetryUtil.getIpAddr(request);
        
        // 检查是否来自Docker内部网络
        boolean isInternalNetwork = DockerNetworkUtil.isInDockerNetwork(clientIp);
        
        // 如果是内部网络请求且带有正确的标识头，直接通过
        if (isInternalNetwork && "true".equals(adminFlag) && StringUtils.hasText(internalService)) {
            log.info("内部服务请求通过认证检查 - 服务: {}, IP: {}", internalService, clientIp);
            return joinPoint.proceed();
        }
        
        String token = PoetryUtil.getTokenWithoutBearer();
        if (!StringUtils.hasText(token)) {
            log.warn("Token为空，用户未登录");
            throw new PoetryLoginException(CodeMsg.NOT_LOGIN.getMsg());
        }

        // 使用PoetryUtil获取用户信息（已集成Redis缓存）
        User user = PoetryUtil.getCurrentUser();
        if (user == null) {
            log.warn("Token无效或已过期: {}", token);
            throw new PoetryLoginException(CodeMsg.LOGIN_EXPIRED.getMsg());
        }

        // 验证token类型和权限 - 使用更安全的startsWith()方法防止权限绕过攻击
        if (TokenValidationUtil.isUserToken(token)) {
            if (loginCheck.value() == PoetryEnum.USER_TYPE_ADMIN.getCode() || loginCheck.value() == PoetryEnum.USER_TYPE_DEV.getCode()) {
                log.warn("普通用户尝试访问管理员接口 - 用户: {}, IP: {}, token前缀: {}",
                    user.getUsername(), clientIp, TokenValidationUtil.getTokenPrefix(token));
                return PoetryResult.fail("请输入管理员账号！");
            }
        } else if (TokenValidationUtil.isAdminToken(token)) {
            log.info("管理员请求 - IP: {}, 用户: {}", clientIp, user.getUsername());
            // 检查是否为需要超级管理员权限的接口（保留某些特殊接口的限制）
            if (loginCheck.value() == PoetryEnum.USER_TYPE_ADMIN.getCode()) {
                // 对于@LoginCheck(0)的接口，检查用户是否为管理员类型
                if (user.getUserType() != PoetryEnum.USER_TYPE_ADMIN.getCode() &&
                    user.getUserType() != PoetryEnum.USER_TYPE_DEV.getCode()) {
                    log.warn("非管理员用户尝试访问管理员接口 - 用户: {}, userType: {}, IP: {}",
                            user.getUsername(), user.getUserType(), clientIp);
                    return PoetryResult.fail("请输入管理员账号！");
                }
                log.info("管理员用户访问管理员接口 - 用户: {}, userType: {}, IP: {}",
                        user.getUsername(), user.getUserType(), clientIp);
            }
        } else {
            log.warn("无效的token类型或格式 - IP: {}, token前缀: {}, token长度: {}",
                clientIp, TokenValidationUtil.getTokenPrefix(token), token.length());
            throw new PoetryLoginException(CodeMsg.NOT_LOGIN.getMsg());
        }

        if (loginCheck.value() < user.getUserType()) {
            log.warn("用户权限不足 - 需要权限: {}, 当前权限: {}", loginCheck.value(), user.getUserType());
            throw new PoetryRuntimeException("权限不足！");
        }

        // 重置过期时间 - 使用Redis缓存替换PoetryCache
        try {
            Integer userId = user.getId();
            boolean needRefresh = false;

            // 检查是否需要刷新token间隔
            if (TokenValidationUtil.isUserToken(token)) {
                String intervalKey = CacheConstants.buildUserTokenIntervalKey(userId);
                needRefresh = cacheService.get(intervalKey) == null;
            } else if (TokenValidationUtil.isAdminToken(token)) {
                String intervalKey = CacheConstants.buildAdminTokenIntervalKey(userId);
                needRefresh = cacheService.get(intervalKey) == null;
            }

            if (needRefresh) {
                synchronized (userId.toString().intern()) {
                    boolean shouldRefresh = false;

                    // 双重检查锁定模式
                    if (TokenValidationUtil.isUserToken(token)) {
                        String intervalKey = CacheConstants.buildUserTokenIntervalKey(userId);
                        shouldRefresh = cacheService.get(intervalKey) == null;
                    } else if (TokenValidationUtil.isAdminToken(token)) {
                        String intervalKey = CacheConstants.buildAdminTokenIntervalKey(userId);
                        shouldRefresh = cacheService.get(intervalKey) == null;
                    }

                    if (shouldRefresh) {
                        log.info("刷新token过期时间 - 用户: {}, token类型: {}, userId: {}",
                            user.getUsername(), TokenValidationUtil.getTokenType(token).getDescription(), userId);

                        // 刷新用户会话缓存
                        cacheService.cacheUserSession(token, userId);
                        cacheService.cacheUser(user);

                        if (TokenValidationUtil.isUserToken(token)) {
                            // 刷新用户token相关缓存
                            cacheService.cacheUserToken(userId, token);
                            cacheService.cacheTokenInterval(userId, false);
                            log.debug("刷新用户token缓存: userId={}", userId);
                        } else if (TokenValidationUtil.isAdminToken(token)) {
                            // 刷新管理员token相关缓存
                            cacheService.cacheAdminToken(userId, token);
                            cacheService.cacheTokenInterval(userId, true);
                            log.debug("刷新管理员token缓存: userId={}", userId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("刷新token过期时间时发生错误: userId={}, token={}", user.getId(), token, e);
            // 发生错误时不阻止请求继续执行
        }

        // 将用户信息设置到request attribute中，供Controller使用
        request.setAttribute("currentUser", user);
        
        return joinPoint.proceed();
    }
}
