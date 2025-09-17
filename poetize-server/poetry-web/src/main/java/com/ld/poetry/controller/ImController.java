package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.entity.User;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.utils.Result;
import com.ld.poetry.utils.SecureTokenGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * WebSocket IM 控制器
 * 处理聊天室相关的API请求
 */
@RestController
@RequestMapping("/im")
public class ImController {

    @Autowired
    private CacheService cacheService;

    /**
     * 获取WebSocket连接临时token
     * 有效期30分钟，专门用于WebSocket握手
     */
    @GetMapping("/getWsToken")
    @LoginCheck
    public Result<String> getWebSocketToken(HttpServletRequest request) {
        try {
            // 从请求中获取当前用户信息
            User currentUser = (User) request.getAttribute("currentUser");
            if (currentUser == null) {
                return Result.fail("用户未登录");
            }

            // 生成30分钟有效期的WebSocket临时token
            String wsToken = SecureTokenGenerator.generateWebSocketToken(
                currentUser.getId(), 
                currentUser.getUserType()
            );

            // 将临时token存储到缓存中，有效期30分钟
            cacheService.cacheWebSocketSession(wsToken, currentUser.getId(), 30 * 60);

            return Result.ok("获取WebSocket token成功", wsToken);
            
        } catch (Exception e) {
            return Result.fail("获取WebSocket token失败: " + e.getMessage());
        }
    }

    /**
     * 验证WebSocket token有效性
     */
    @GetMapping("/validateWsToken")
    public Result<Boolean> validateWebSocketToken(String wsToken) {
        try {
            if (!StringUtils.hasText(wsToken)) {
                return Result.fail("Token不能为空");
            }

            // 验证token格式和签名
            Integer userId = SecureTokenGenerator.validateWebSocketToken(wsToken);
            if (userId == null) {
                return Result.fail("Token无效或已过期");
            }

            // 检查缓存中是否存在
            Integer cachedUserId = cacheService.getUserIdFromWebSocketSession(wsToken);
            if (cachedUserId == null || !cachedUserId.equals(userId)) {
                return Result.fail("Token已失效");
            }

            return Result.ok("Token验证成功", true);
            
        } catch (Exception e) {
            return Result.fail("Token验证失败: " + e.getMessage());
        }
    }

    /**
     * 续签WebSocket token
     * 在token即将过期时调用，延长有效期
     */
    @GetMapping("/renewWsToken")
    public Result<String> renewWebSocketToken(String oldToken) {
        try {
            if (!StringUtils.hasText(oldToken)) {
                return Result.fail("旧Token不能为空");
            }

            // 验证旧token（允许即将过期的token进行续签）
            Integer userId = SecureTokenGenerator.validateWebSocketToken(oldToken, true);
            if (userId == null) {
                return Result.fail("无效的token，无法续签");
            }

            // 检查缓存中的token是否存在
            Integer cachedUserId = cacheService.getUserIdFromWebSocketSession(oldToken);
            if (cachedUserId == null || !cachedUserId.equals(userId)) {
                return Result.fail("Token已失效，无法续签");
            }

            // 获取用户类型（从token中解析）
            String userType = SecureTokenGenerator.getUserTypeFromWebSocketToken(oldToken);
            
            // 生成新的WebSocket token
            String newToken = SecureTokenGenerator.generateWebSocketToken(userId, userType);
            
            // 缓存新token，有效期30分钟
            cacheService.cacheWebSocketSession(newToken, userId, 30 * 60);
            
            // 删除旧token缓存（避免token堆积）
            cacheService.removeWebSocketSession(oldToken);
            
            return Result.ok("Token续签成功", newToken);
            
        } catch (Exception e) {
            return Result.fail("Token续签失败: " + e.getMessage());
        }
    }

    /**
     * 检查WebSocket token剩余有效时间
     * 返回剩余分钟数，用于前端判断是否需要续签
     */
    @GetMapping("/checkWsTokenExpiry")
    public Result<Integer> checkWebSocketTokenExpiry(String wsToken) {
        try {
            if (!StringUtils.hasText(wsToken)) {
                return Result.fail("Token不能为空");
            }

            // 获取token剩余有效时间（分钟）
            int remainingMinutes = SecureTokenGenerator.getWebSocketTokenRemainingMinutes(wsToken);
            
            if (remainingMinutes <= 0) {
                return Result.fail("Token已过期");
            }
            
            return Result.ok("获取token有效期成功", remainingMinutes);
            
        } catch (Exception e) {
            return Result.fail("检查token有效期失败: " + e.getMessage());
        }
    }

    /**
     * WebSocket心跳检测并续签
     * 用于保持连接活跃并自动续签token
     */
    @GetMapping("/heartbeat")
    public Result<String> heartbeat(String wsToken) {
        try {
            if (!StringUtils.hasText(wsToken)) {
                return Result.fail("Token不能为空");
            }

            // 验证当前token
            Integer userId = SecureTokenGenerator.validateWebSocketToken(wsToken);
            if (userId == null) {
                return Result.fail("Token无效");
            }

            // 检查剩余有效时间
            int remainingMinutes = SecureTokenGenerator.getWebSocketTokenRemainingMinutes(wsToken);
            
            // 如果剩余时间少于5分钟，自动续签
            if (remainingMinutes <= 5) {
                String userType = SecureTokenGenerator.getUserTypeFromWebSocketToken(wsToken);
                String newToken = SecureTokenGenerator.generateWebSocketToken(userId, userType);
                
                // 缓存新token
                cacheService.cacheWebSocketSession(newToken, userId, 30 * 60);
                
                // 删除旧token
                cacheService.removeWebSocketSession(wsToken);
                
                return Result.ok("心跳检测成功，token已自动续签", newToken);
            } else {
                // 更新缓存过期时间（重置为30分钟）
                cacheService.cacheWebSocketSession(wsToken, userId, 30 * 60);
                return Result.ok("心跳检测成功", wsToken);
            }
            
        } catch (Exception e) {
            return Result.fail("心跳检测失败: " + e.getMessage());
        }
    }
}