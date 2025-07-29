package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.aop.SaveCheck;
import com.ld.poetry.entity.User;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.MailService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户信息表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-12
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private MailService mailService;

    @Autowired
    private CacheService cacheService;

    /**
     * 用户名/密码注册
     */
    @PostMapping("/regist")
    public PoetryResult<UserVO> regist(@Validated @RequestBody UserVO user) {
        return userService.regist(user);
    }


    /**
     * 用户名、邮箱、手机号/密码登录
     */
    @PostMapping("/login")
    public PoetryResult<UserVO> login(@RequestParam("account") String account,
                                      @RequestParam("password") String password,
                                      @RequestParam(value = "isAdmin", defaultValue = "false") Boolean isAdmin) {
        return userService.login(account, password, isAdmin);
    }


    /**
     * Token登录
     */
    @PostMapping("/token")
    public PoetryResult<UserVO> login(@RequestParam("userToken") String userToken) {
        return userService.token(userToken);
    }


    /**
     * 退出
     */
    @GetMapping("/logout")
    @LoginCheck
    public PoetryResult exit() {
        return userService.exit();
    }


    /**
     * 检查是否拥有站长权限
     */
    @GetMapping("/checkAdminAuth")
    @LoginCheck(0)
    public PoetryResult<Boolean> checkAdminAuth() {
        // 获取当前用户
        User user = PoetryUtil.getCurrentUser();
        
        // 检查是否站长或管理员账号
        if (user.getUserType() != 0 && user.getUserType() != 1) {
            return PoetryResult.fail("权限不足");
        }
        
        // 检查token是否过期 - 使用Redis缓存验证
        String token = PoetryUtil.getTokenWithoutBearer();
        if (token == null || token.isEmpty()) {
            return PoetryResult.fail("未登录或token无效");
        }

        try {
            // 使用CacheService检查token是否在Redis缓存中
            Integer userId = cacheService.getUserIdFromSession(token);
            if (userId == null) {
                return PoetryResult.fail("登录已过期，请重新登录");
            }

            // 验证用户信息是否存在
            User cachedUser = cacheService.getCachedUser(userId);
            if (cachedUser == null) {
                return PoetryResult.fail("用户信息已过期，请重新登录");
            }

            log.debug("Token验证成功: userId={}, token={}", userId, token);
        } catch (Exception e) {
            log.error("Token验证时发生错误: token={}", token, e);
            return PoetryResult.fail("Token验证失败，请重新登录");
        }
        
        return PoetryResult.success(true);
    }


    /**
     * 更新用户信息
     */
    @PostMapping("/updateUserInfo")
    @LoginCheck
    public PoetryResult<UserVO> updateUserInfo(@RequestBody UserVO user) {
        try {
            Integer userId = PoetryUtil.getUserId();
            // 使用CacheService清理用户缓存
            cacheService.evictUser(userId);
            log.debug("清理用户信息缓存: userId={}", userId);
        } catch (Exception e) {
            log.error("清理用户信息缓存时发生错误: userId={}", PoetryUtil.getUserId(), e);
        }
        return userService.updateUserInfo(user);
    }

    /**
     * 获取验证码
     * <p>
     * 1 手机号
     * 2 邮箱
     */
    @GetMapping("/getCode")
    @LoginCheck
    @SaveCheck
    public PoetryResult getCode(@RequestParam("flag") Integer flag) {
        return userService.getCode(flag);
    }

    /**
     * 绑定手机号或者邮箱
     * <p>
     * 1 手机号
     * 2 邮箱
     */
    @GetMapping("/getCodeForBind")
    @LoginCheck
    @SaveCheck
    public PoetryResult getCodeForBind(@RequestParam("place") String place, @RequestParam("flag") Integer flag) {
        return userService.getCodeForBind(place, flag);
    }

    /**
     * 修改密钥信息(手机号、邮箱、密码)
     * <p>
     * 1 手机号
     * 2 邮箱
     * 3 密码：place=老密码&password=新密码
     */
    @PostMapping("/updateSecretInfo")
    @LoginCheck
    public PoetryResult<UserVO> updateSecretInfo(@RequestParam("place") String place, @RequestParam("flag") Integer flag, @RequestParam(value = "code", required = false) String code, @RequestParam("password") String password) {
        // 1. 先获取当前用户信息用于后续缓存处理
        User currentUser = PoetryUtil.getCurrentUser();
        if (currentUser == null) {
            log.error("用户上下文验证失败 - 无法获取当前用户信息, place={}, flag={}", place, flag);
            return PoetryResult.fail("用户认证失败，请重新登录");
        }

        Integer userId = currentUser.getId();
        log.debug("准备更新用户密钥信息: userId={}, place={}, flag={}", userId, place, flag);

        // 2. 调用 service 执行真正的更新操作
        PoetryResult<UserVO> result = userService.updateSecretInfo(place, flag, code, password);

        // 3. 仅当更新成功时才清理缓存，避免 service 内再次获取用户信息出现空指针
        if (result.getCode() == 200) {
            try {
                cacheService.evictUser(userId);
                log.debug("更新密钥信息成功，清理用户缓存: userId={}", userId);
            } catch (Exception e) {
                log.error("清理用户密钥信息缓存时发生错误: userId={}", userId, e);
            }
        }

        return result;
    }

    /**
     * 忘记密码 获取验证码
     * <p>
     * 1 手机号
     * 2 邮箱
     */
    @GetMapping("/getCodeForForgetPassword")
    @SaveCheck
    public PoetryResult getCodeForForgetPassword(@RequestParam("place") String place, @RequestParam("flag") Integer flag) {
        return userService.getCodeForForgetPassword(place, flag);
    }

    /**
     * 忘记密码 更新密码
     * <p>
     * 1 手机号
     * 2 邮箱
     */
    @PostMapping("/updateForForgetPassword")
    public PoetryResult updateForForgetPassword(@RequestParam("place") String place, @RequestParam("flag") Integer flag, @RequestParam("code") String code, @RequestParam("password") String password) {
        return userService.updateForForgetPassword(place, flag, code, password);
    }

    /**
     * 根据用户名查找用户信息
     */
    @GetMapping("/getUserByUsername")
    @LoginCheck
    public PoetryResult<List<UserVO>> getUserByUsername(@RequestParam("username") String username) {
        return userService.getUserByUsername(username);
    }

    /**
     * 订阅/取消订阅专栏（标签）
     * <p>
     * flag = true：订阅
     * flag = false：取消订阅
     */
    @GetMapping("/subscribe")
    @LoginCheck
    public PoetryResult<UserVO> subscribe(@RequestParam("labelId") Integer labelId, @RequestParam("flag") Boolean flag) {
        // 先执行订阅操作
        PoetryResult<UserVO> result = userService.subscribe(labelId, flag);

        // 订阅操作成功后更新缓存中的用户信息
        if (result.getCode() == 200 && result.getData() != null) {
            try {
                Integer userId = PoetryUtil.getUserId();

                // 从数据库重新获取最新的用户信息
                User updatedUser = userService.getById(userId);
                if (updatedUser != null) {
                    // 重新缓存更新后的用户信息，而不是简单删除缓存
                    cacheService.cacheUser(updatedUser);
                    log.debug("订阅操作成功，更新用户缓存: userId={}, labelId={}, flag={}", userId, labelId, flag);
                } else {
                    // 如果获取不到用户信息，则清除缓存
                    cacheService.evictUser(userId);
                    log.warn("无法获取更新后的用户信息，清除缓存: userId={}", userId);
                }
            } catch (Exception e) {
                log.error("更新用户订阅信息缓存时发生错误: userId={}, labelId={}, flag={}", PoetryUtil.getUserId(), labelId, flag, e);
                // 缓存更新失败不影响订阅操作的结果
            }
        }

        return result;
    }

    /**
     * 第三方登录
     */
    @PostMapping("/thirdLogin")
    public PoetryResult<UserVO> thirdLogin(@RequestBody UserVO thirdUserInfo) {
        return userService.thirdLogin(
            thirdUserInfo.getPlatformType(),
            thirdUserInfo.getUid(),
            thirdUserInfo.getUsername(),
            thirdUserInfo.getEmail(),
            thirdUserInfo.getAvatar()
        );
    }
}

