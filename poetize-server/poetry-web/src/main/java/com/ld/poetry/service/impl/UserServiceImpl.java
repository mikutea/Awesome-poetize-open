package com.ld.poetry.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.UserMapper;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.entity.WeiYan;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.im.http.dao.ImChatGroupUserMapper;
import com.ld.poetry.im.http.dao.ImChatUserFriendMapper;
import com.ld.poetry.im.http.entity.ImChatGroupUser;
import com.ld.poetry.im.http.entity.ImChatUserFriend;
import com.ld.poetry.im.websocket.ImConfigConst;
import com.ld.poetry.im.websocket.TioUtil;
import com.ld.poetry.im.websocket.TioWebsocketStarter;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.PasswordService;
import com.ld.poetry.service.PasswordUpgradeService;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.service.WeiYanService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.tio.core.Tio;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>
 * 用户信息表 服务实现类
 * </p>
 *
 * @author sara
 * @since 2021-08-12
 */
@SuppressWarnings("unchecked")
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private WeiYanService weiYanService;

    @Autowired
    private ImChatGroupUserMapper imChatGroupUserMapper;

    @Autowired
    private ImChatUserFriendMapper imChatUserFriendMapper;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private com.ld.poetry.utils.cache.UserCacheManager userCacheManager;

    @Autowired
    private ThirdPartyOauthConfigService thirdPartyOauthConfigService;

    @Autowired
    private CacheService cacheService;



    @Autowired
    private com.ld.poetry.service.OAuthClientService oAuthClientService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private PasswordUpgradeService passwordUpgradeService;

    @Value("${user.code.format}")
    private String codeFormat;

    /**
     * 检查IP是否在管理员IP白名单中
     * @param ip 客户端IP
     * @return 是否在白名单中
     */
    private boolean isIpInAdminWhitelist(String ip) {
        Set<String> whitelistedIps = (Set<String>) PoetryCache.get(CommonConst.ADMIN_IP_WHITELIST);
        if (whitelistedIps == null || whitelistedIps.isEmpty()) {
            // 初始化时没有白名单，允许第一次登录设置
            return true;
        }
        return whitelistedIps.contains(ip);
    }

    /**
     * 记录管理员登录IP
     * @param ip 客户端IP
     */
    private void recordAdminLoginIp(String ip) {
        Set<String> whitelistedIps = (Set<String>) PoetryCache.get(CommonConst.ADMIN_IP_WHITELIST);
        if (whitelistedIps == null) {
            whitelistedIps = new HashSet<>();
        }
        whitelistedIps.add(ip);
        PoetryCache.put(CommonConst.ADMIN_IP_WHITELIST, whitelistedIps);
    }

    /**
     * 检查账号是否被锁定
     * @param account 账号
     * @return 是否被锁定
     */
    private boolean isAccountLocked(String account) {
        String lockKey = "login_lock_" + account;
        return PoetryCache.get(lockKey) != null;
    }

    /**
     * 记录失败登录尝试
     * @param account 账号
     * @return 当前尝试次数
     */
    private int recordFailedLoginAttempt(String account) {
        String attemptKey = "login_attempt_" + account;
        Integer attempts = (Integer) PoetryCache.get(attemptKey);
        if (attempts == null) {
            attempts = 1;
        } else {
            attempts++;
        }

        // 设置失败尝试记录，过期时间1小时
        PoetryCache.put(attemptKey, attempts, 3600);

        // 如果失败次数超过阈值，锁定账号
        if (attempts >= CommonConst.MAX_LOGIN_ATTEMPTS) {
            String lockKey = "login_lock_" + account;
            PoetryCache.put(lockKey, true, CommonConst.LOGIN_LOCKOUT_TIME);
            log.warn("账号 {} 因多次登录失败被锁定 {} 秒", account, CommonConst.LOGIN_LOCKOUT_TIME);
        }

        return attempts;
    }

    /**
     * 清除失败登录尝试记录
     * @param account 账号
     */
    private void clearFailedLoginAttempts(String account) {
        String attemptKey = "login_attempt_" + account;
        PoetryCache.remove(attemptKey);
    }

    @Override
    public PoetryResult<UserVO> login(String account, String password, Boolean isAdmin) {
        // 获取客户端IP
        String clientIp = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
        log.info("用户登录尝试 - 用户名: {}, IP: {}, 是否管理员: {}", account, clientIp, isAdmin);

        // 检查账号是否被锁定
        String lockKey = "login_lock_" + account;
        if (PoetryCache.get(lockKey) != null) {
            log.warn("账号已被锁定 - 用户名: {}, IP: {}", account, clientIp);
            return PoetryResult.fail("账号已被锁定，请稍后再试");
        }

        // 验证用户名和密码
        User one = lambdaQuery().eq(User::getUsername, account).one();
        if (one == null) {
            log.warn("登录失败 - 用户不存在: {}, IP: {}", account, clientIp);
            return PoetryResult.fail("用户名或密码错误！");
        }

        // 解密前端传来的AES加密密码
        String decryptedPassword;
        try {
            decryptedPassword = passwordService.decryptFromFrontend(password);
        } catch (Exception e) {
            log.warn("密码解密失败 - 用户名: {}, IP: {}", account, clientIp);
            return PoetryResult.fail("密码格式错误！");
        }

        // 使用新的密码验证服务
        if (!passwordService.matches(decryptedPassword, one.getPassword())) {
            int attempts = recordFailedLoginAttempt(account);
            log.warn("登录失败 - 密码错误: {}, IP: {}, 失败次数: {}", account, clientIp, attempts);
            return PoetryResult.fail("用户名或密码错误！");
        }

        // 登录成功，清除失败记录
        clearFailedLoginAttempts(account);
        log.info("用户登录成功 - 用户名: {}, IP: {}", account, clientIp);

        // 检查密码是否需要升级（MD5 -> BCrypt）
        if (passwordService.needsUpgrade(one.getPassword())) {
            try {
                String upgradedPassword = passwordService.upgradePassword(decryptedPassword, one.getPassword());
                // 更新数据库中的密码
                lambdaUpdate().eq(User::getId, one.getId())
                    .set(User::getPassword, upgradedPassword)
                    .update();
                // 更新内存中的用户对象
                one.setPassword(upgradedPassword);
                // 记录密码升级统计
                passwordUpgradeService.recordPasswordUpgrade();
                log.info("用户密码已从MD5升级到BCrypt - 用户名: {}", account);
            } catch (Exception e) {
                log.error("密码升级失败 - 用户名: {}, 错误: {}", account, e.getMessage());
                // 密码升级失败不影响登录，只记录日志
            }
        }

        // 检查管理员权限
        if (isAdmin && (one.getUserType() == PoetryEnum.USER_TYPE_ADMIN.getCode() || one.getUserType() == PoetryEnum.USER_TYPE_DEV.getCode())) {
            recordAdminLoginIp(clientIp);
            log.info("管理员登录成功 - 用户名: {}, IP: {}", account, clientIp);
        }

        String adminToken = "";
        String userToken = "";

        // 清除旧token
        if (isAdmin) {
            if (one.getUserType() != PoetryEnum.USER_TYPE_ADMIN.getCode() && one.getUserType() != PoetryEnum.USER_TYPE_DEV.getCode()) {
                log.warn("非管理员尝试管理员登录 - 用户名: {}, IP: {}", account, clientIp);
                return PoetryResult.fail("请输入管理员账号！");
            }
            // 清除可能存在的旧token
            if (PoetryCache.get(CommonConst.ADMIN_TOKEN + one.getId()) != null) {
                String oldToken = (String) PoetryCache.get(CommonConst.ADMIN_TOKEN + one.getId());
                log.info("清除旧的管理员token - 用户: {}, 旧token: {}", account, oldToken);
                PoetryCache.remove(oldToken);
                PoetryCache.remove(CommonConst.ADMIN_TOKEN + one.getId());
                PoetryCache.remove(CommonConst.ADMIN_TOKEN_INTERVAL + one.getId());
            }
        } else {
            // 清除可能存在的旧token
            if (PoetryCache.get(CommonConst.USER_TOKEN + one.getId()) != null) {
                String oldToken = (String) PoetryCache.get(CommonConst.USER_TOKEN + one.getId());
                log.info("清除旧的用户token - 用户: {}, 旧token: {}", account, oldToken);
                PoetryCache.remove(oldToken);
                PoetryCache.remove(CommonConst.USER_TOKEN + one.getId());
                PoetryCache.remove(CommonConst.USER_TOKEN_INTERVAL + one.getId());
            }
        }

        // 生成新token
        if (isAdmin && !StringUtils.hasText(adminToken)) {
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            adminToken = CommonConst.ADMIN_ACCESS_TOKEN + uuid;
            log.info("生成新的管理员token - 用户: {}, token: {}", account, adminToken);

            // 使用Redis缓存替换PoetryCache
            cacheService.cacheUserSession(adminToken, one.getId());
            cacheService.cacheUserTokenMapping(one.getId(), adminToken);
            cacheService.cacheUser(one);

            // 保持UserCacheManager兼容性
            userCacheManager.cacheUserByToken(adminToken, one);
            userCacheManager.cacheUserById(one.getId(), one);
        } else if (!isAdmin && !StringUtils.hasText(userToken)) {
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            userToken = CommonConst.USER_ACCESS_TOKEN + uuid;
            log.info("生成新的用户token - 用户: {}, token: {}", account, userToken);

            // 使用Redis缓存替换PoetryCache
            cacheService.cacheUserSession(userToken, one.getId());
            cacheService.cacheUserTokenMapping(one.getId(), userToken);
            cacheService.cacheUser(one);

            // 保持UserCacheManager兼容性
            userCacheManager.cacheUserByToken(userToken, one);
            userCacheManager.cacheUserById(one.getId(), one);
        }

        // 构建返回数据
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setUserType(one.getUserType());
        if (isAdmin && one.getUserType() == PoetryEnum.USER_TYPE_ADMIN.getCode()) {
            userVO.setIsBoss(true);
        }

        if (isAdmin) {
            userVO.setAccessToken(adminToken);
        } else {
            userVO.setAccessToken(userToken);
        }
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult exit() {
        String token = PoetryUtil.getToken();
        Integer userId = PoetryUtil.getUserId();
        log.info("用户退出登录 - 用户ID: {}, token: {}", userId, token);

        // 使用Redis缓存清理替换PoetryCache
        cacheService.evictUserSession(token);
        cacheService.evictUserTokenMapping(userId);
        cacheService.evictUser(userId);

        if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
            TioWebsocketStarter tioWebsocketStarter = TioUtil.getTio();
            if (tioWebsocketStarter != null) {
                Tio.removeUser(tioWebsocketStarter.getServerTioConfig(), String.valueOf(userId), "用户退出登录");
            }
        }

        // 清除UserCacheManager中的用户缓存
        userCacheManager.removeUserByToken(token);
        userCacheManager.removeUserById(userId);

        return PoetryResult.success();
    }

    @Override
    public PoetryResult<UserVO> regist(UserVO user) {
        String regex = "\\d{11}";
        if (user.getUsername().matches(regex)) {
            return PoetryResult.fail("用户名不能为11位数字！");
        }

        if (user.getUsername().contains("@")) {
            return PoetryResult.fail("用户名不能包含@！");
        }

        if (StringUtils.hasText(user.getPhoneNumber()) && StringUtils.hasText(user.getEmail())) {
            return PoetryResult.fail("手机号与邮箱只能选择其中一个！");
        }

        if (StringUtils.hasText(user.getPhoneNumber())) {
            Integer codeCache = (Integer) PoetryCache.get(CommonConst.FORGET_PASSWORD + user.getPhoneNumber() + "_1");
            if (codeCache == null || codeCache != Integer.parseInt(user.getCode())) {
                return PoetryResult.fail("验证码错误！");
            }
            PoetryCache.remove(CommonConst.FORGET_PASSWORD + user.getPhoneNumber() + "_1");
        } else if (StringUtils.hasText(user.getEmail())) {
            Integer codeCache = (Integer) PoetryCache.get(CommonConst.FORGET_PASSWORD + user.getEmail() + "_2");
            if (codeCache == null || codeCache != Integer.parseInt(user.getCode())) {
                return PoetryResult.fail("验证码错误！");
            }
            PoetryCache.remove(CommonConst.FORGET_PASSWORD + user.getEmail() + "_2");
        } else {
            return PoetryResult.fail("请输入邮箱或手机号！");
        }


        // 解密前端传来的AES加密密码
        String decryptedPassword;
        try {
            decryptedPassword = passwordService.decryptFromFrontend(user.getPassword());
        } catch (Exception e) {
            log.warn("注册时密码解密失败");
            return PoetryResult.fail("密码格式错误！");
        }

        // 验证密码是否有效（仅检查非空）
        if (!passwordService.isPasswordValid(decryptedPassword)) {
            return PoetryResult.fail("密码不能为空！");
        }

        Long count = lambdaQuery().eq(User::getUsername, user.getUsername()).count();
        if (count != 0) {
            return PoetryResult.fail("用户名重复！");
        }
        if (StringUtils.hasText(user.getPhoneNumber())) {
            Long phoneNumberCount = lambdaQuery().eq(User::getPhoneNumber, user.getPhoneNumber()).count();
            if (phoneNumberCount != 0) {
                return PoetryResult.fail("手机号重复！");
            }
        } else if (StringUtils.hasText(user.getEmail())) {
            Long emailCount = lambdaQuery().eq(User::getEmail, user.getEmail()).count();
            if (emailCount != 0) {
                return PoetryResult.fail("邮箱重复！");
            }
        }

        User u = new User();
        u.setUsername(user.getUsername());
        u.setPhoneNumber(user.getPhoneNumber());
        u.setEmail(user.getEmail());
        // 新用户直接使用BCrypt加密密码
        u.setPassword(passwordService.encodeBCrypt(decryptedPassword));
        u.setAvatar(PoetryUtil.getRandomAvatar(null));
        save(u);

        User one = lambdaQuery().eq(User::getId, u.getId()).one();

        String userToken = CommonConst.USER_ACCESS_TOKEN + UUID.randomUUID().toString().replaceAll("-", "");

        // 使用Redis缓存替换PoetryCache
        cacheService.cacheUserSession(userToken, one.getId());
        cacheService.cacheUserTokenMapping(one.getId(), userToken);
        cacheService.cacheUser(one);

        // 缓存用户信息到UserCacheManager
        userCacheManager.cacheUserByToken(userToken, one);
        userCacheManager.cacheUserById(one.getId(), one);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setUserType(one.getUserType());
        userVO.setAccessToken(userToken);

        WeiYan weiYan = new WeiYan();
        weiYan.setUserId(one.getId());
        weiYan.setContent("到此一游");
        weiYan.setType(CommonConst.WEIYAN_TYPE_FRIEND);
        weiYan.setIsPublic(Boolean.TRUE);
        weiYanService.save(weiYan);

        ImChatGroupUser imChatGroupUser = new ImChatGroupUser();
        imChatGroupUser.setGroupId(ImConfigConst.DEFAULT_GROUP_ID);
        imChatGroupUser.setUserId(one.getId());
        imChatGroupUser.setUserStatus(ImConfigConst.GROUP_USER_STATUS_PASS);
        imChatGroupUserMapper.insert(imChatGroupUser);

        ImChatUserFriend imChatUser = new ImChatUserFriend();
        imChatUser.setUserId(one.getId());
        imChatUser.setFriendId(PoetryUtil.getAdminUser().getId());
        imChatUser.setRemark("站长");
        imChatUser.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
        imChatUserFriendMapper.insert(imChatUser);

        ImChatUserFriend imChatFriend = new ImChatUserFriend();
        imChatFriend.setUserId(PoetryUtil.getAdminUser().getId());
        imChatFriend.setFriendId(one.getId());
        imChatFriend.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
        imChatUserFriendMapper.insert(imChatFriend);

        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult<UserVO> updateUserInfo(UserVO user) {
        if (StringUtils.hasText(user.getUsername())) {
            String regex = "\\d{11}";
            if (user.getUsername().matches(regex)) {
                return PoetryResult.fail("用户名不能为11位数字！");
            }

            if (user.getUsername().contains("@")) {
                return PoetryResult.fail("用户名不能包含@！");
            }

            Long count = lambdaQuery().eq(User::getUsername, user.getUsername()).ne(User::getId, PoetryUtil.getUserId()).count();
            if (count != 0) {
                return PoetryResult.fail("用户名重复！");
            }
        }
        User u = new User();
        u.setId(PoetryUtil.getUserId());
        u.setUsername(user.getUsername());
        u.setAvatar(user.getAvatar());
        u.setGender(user.getGender());
        u.setIntroduction(user.getIntroduction());
        updateById(u);
        User one = lambdaQuery().eq(User::getId, u.getId()).one();
        String token = PoetryUtil.getToken();

        // 使用Redis缓存替换PoetryCache
        cacheService.cacheUserSession(token, one.getId());
        cacheService.cacheUserTokenMapping(one.getId(), token);
        cacheService.cacheUser(one);

        // 更新UserCacheManager中的用户缓存
        userCacheManager.cacheUserByToken(token, one);
        userCacheManager.cacheUserById(one.getId(), one);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setUserType(one.getUserType());
        userVO.setAccessToken(PoetryUtil.getToken());
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult getCode(Integer flag) {
        User user = PoetryUtil.getCurrentUser();
        int i = new Random().nextInt(900000) + 100000;
        if (flag == 1) {
            if (!StringUtils.hasText(user.getPhoneNumber())) {
                return PoetryResult.fail("请先绑定手机号！");
            }

            log.info(user.getId() + "---" + user.getUsername() + "---" + "手机验证码---" + i);
        } else if (flag == 2) {
            if (!StringUtils.hasText(user.getEmail())) {
                return PoetryResult.fail("请先绑定邮箱！");
            }

            log.info(user.getId() + "---" + user.getUsername() + "---" + "邮箱验证码---" + i);

            List<String> mail = new ArrayList<>();
            mail.add(user.getEmail());
            String text = getCodeMail(i);
            WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);

            // 检查邮箱配置是否存在
            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            AtomicInteger count = (AtomicInteger) PoetryCache.get(CommonConst.CODE_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                if (count == null) {
                    PoetryCache.put(CommonConst.CODE_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }
        }
        PoetryCache.put(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + flag, Integer.valueOf(i), 300);
        return PoetryResult.success();
    }

    @Override
    public PoetryResult getCodeForBind(String place, Integer flag) {
        int i = new Random().nextInt(900000) + 100000;
        if (flag == 1) {
            log.info(place + "---" + "手机验证码---" + i);
        } else if (flag == 2) {
            log.info(place + "---" + "邮箱验证码---" + i);
            List<String> mail = new ArrayList<>();
            mail.add(place);
            String text = getCodeMail(i);
            WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);

            // 检查邮箱配置是否存在
            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            AtomicInteger count = (AtomicInteger) PoetryCache.get(CommonConst.CODE_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                if (count == null) {
                    PoetryCache.put(CommonConst.CODE_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }
        }
        PoetryCache.put(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag, Integer.valueOf(i), 300);
        return PoetryResult.success();
    }

    @Override
    public PoetryResult<UserVO> updateSecretInfo(String place, Integer flag, String code, String password) {
        // 解密前端传来的AES加密密码
        String decryptedPassword;
        try {
            decryptedPassword = passwordService.decryptFromFrontend(password);
        } catch (Exception e) {
            log.warn("更新密码时密码解密失败");
            return PoetryResult.fail("密码格式错误！");
        }

        User user = PoetryUtil.getCurrentUser();
        // 使用新的密码验证服务验证当前密码
        if ((flag == 1 || flag == 2) && !passwordService.matches(decryptedPassword, user.getPassword())) {
            return PoetryResult.fail("密码错误！");
        }
        if ((flag == 1 || flag == 2) && !StringUtils.hasText(code)) {
            return PoetryResult.fail("请输入验证码！");
        }
        User updateUser = new User();
        updateUser.setId(user.getId());
        if (flag == 1) {
            Long count = lambdaQuery().eq(User::getPhoneNumber, place).count();
            if (count != 0) {
                return PoetryResult.fail("手机号重复！");
            }
            Integer codeCache = (Integer) PoetryCache.get(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag);
            if (codeCache != null && codeCache.intValue() == Integer.parseInt(code)) {

                PoetryCache.remove(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag);

                updateUser.setPhoneNumber(place);
            } else {
                return PoetryResult.fail("验证码错误！");
            }

        } else if (flag == 2) {
            Long count = lambdaQuery().eq(User::getEmail, place).count();
            if (count != 0) {
                return PoetryResult.fail("邮箱重复！");
            }
            Integer codeCache = (Integer) PoetryCache.get(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag);
            if (codeCache != null && codeCache.intValue() == Integer.parseInt(code)) {

                PoetryCache.remove(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag);

                updateUser.setEmail(place);
            } else {
                return PoetryResult.fail("验证码错误！");
            }
        } else if (flag == 3) {
            // flag == 3 表示修改密码，place是旧密码，password是新密码
            String oldPassword;
            try {
                oldPassword = passwordService.decryptFromFrontend(place);
            } catch (Exception e) {
                log.warn("旧密码解密失败");
                return PoetryResult.fail("旧密码格式错误！");
            }

            // 验证旧密码
            if (!passwordService.matches(oldPassword, user.getPassword())) {
                return PoetryResult.fail("旧密码错误！");
            }

            // 验证新密码是否有效（仅检查非空）
            if (!passwordService.isPasswordValid(decryptedPassword)) {
                return PoetryResult.fail("新密码不能为空！");
            }

            // 使用BCrypt加密新密码
            updateUser.setPassword(passwordService.encodeBCrypt(decryptedPassword));
            log.info("用户修改密码成功 - 用户ID: {}", user.getId());
        }
        updateById(updateUser);

        User one = lambdaQuery().eq(User::getId, user.getId()).one();
        PoetryCache.put(PoetryUtil.getToken(), one, CommonConst.TOKEN_EXPIRE);
        PoetryCache.put(CommonConst.USER_TOKEN + one.getId(), PoetryUtil.getToken(), CommonConst.TOKEN_EXPIRE);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult getCodeForForgetPassword(String place, Integer flag) {
        int i = new Random().nextInt(900000) + 100000;
        if (flag == 1) {
            log.info(place + "---" + "手机验证码---" + i);
        } else if (flag == 2) {
            log.info(place + "---" + "邮箱验证码---" + i);

            List<String> mail = new ArrayList<>();
            mail.add(place);
            String text = getCodeMail(i);
            WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);

            // 检查邮箱配置是否存在
            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            AtomicInteger count = (AtomicInteger) PoetryCache.get(CommonConst.CODE_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                if (count == null) {
                    PoetryCache.put(CommonConst.CODE_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }
        }
        PoetryCache.put(CommonConst.FORGET_PASSWORD + place + "_" + flag, Integer.valueOf(i), 300);
        return PoetryResult.success();
    }

    @Override
    public PoetryResult updateForForgetPassword(String place, Integer flag, String code, String password) {
        // 解密前端传来的AES加密密码
        String decryptedPassword;
        try {
            decryptedPassword = passwordService.decryptFromFrontend(password);
        } catch (Exception e) {
            log.warn("忘记密码重置时密码解密失败");
            return PoetryResult.fail("密码格式错误！");
        }

        // 验证密码是否有效（仅检查非空）
        if (!passwordService.isPasswordValid(decryptedPassword)) {
            return PoetryResult.fail("密码不能为空！");
        }

        Integer codeCache = (Integer) PoetryCache.get(CommonConst.FORGET_PASSWORD + place + "_" + flag);
        if (codeCache == null || codeCache != Integer.parseInt(code)) {
            return PoetryResult.fail("验证码错误！");
        }

        PoetryCache.remove(CommonConst.FORGET_PASSWORD + place + "_" + flag);

        // 使用BCrypt加密新密码
        String encodedPassword = passwordService.encodeBCrypt(decryptedPassword);

        if (flag == 1) {
            User user = lambdaQuery().eq(User::getPhoneNumber, place).one();
            if (user == null) {
                return PoetryResult.fail("该手机号未绑定账号！");
            }

            if (!user.getUserStatus()) {
                return PoetryResult.fail("账号被冻结！");
            }

            lambdaUpdate().eq(User::getPhoneNumber, place).set(User::getPassword, encodedPassword).update();
            PoetryCache.remove(CommonConst.USER_CACHE + user.getId().toString());
            log.info("用户通过手机号重置密码成功 - 手机号: {}", place);
        } else if (flag == 2) {
            User user = lambdaQuery().eq(User::getEmail, place).one();
            if (user == null) {
                return PoetryResult.fail("该邮箱未绑定账号！");
            }

            if (!user.getUserStatus()) {
                return PoetryResult.fail("账号被冻结！");
            }

            lambdaUpdate().eq(User::getEmail, place).set(User::getPassword, encodedPassword).update();
            PoetryCache.remove(CommonConst.USER_CACHE + user.getId().toString());
            log.info("用户通过邮箱重置密码成功 - 邮箱: {}", place);
        }

        return PoetryResult.success();
    }

    @Override
    public PoetryResult<Page<UserVO>> listUser(BaseRequestVO baseRequestVO) {
        LambdaQueryChainWrapper<User> lambdaQuery = lambdaQuery();

        if (baseRequestVO.getUserStatus() != null) {
            lambdaQuery.eq(User::getUserStatus, baseRequestVO.getUserStatus());
        }

        if (baseRequestVO.getUserType() != null) {
            lambdaQuery.eq(User::getUserType, baseRequestVO.getUserType());
        }

        // 根据是否为第三方登录用户进行筛选
        if (baseRequestVO.getIsThirdPartyUser() != null) {
            if (baseRequestVO.getIsThirdPartyUser()) {
                // 筛选第三方登录用户：platform_type不为空
                lambdaQuery.isNotNull(User::getPlatformType)
                          .ne(User::getPlatformType, "");
            } else {
                // 筛选普通注册用户：platform_type为空或空字符串
                lambdaQuery.and(wrapper -> wrapper.isNull(User::getPlatformType)
                                                 .or()
                                                 .eq(User::getPlatformType, ""));
            }
        }

        if (StringUtils.hasText(baseRequestVO.getSearchKey())) {
            lambdaQuery.and(lq -> lq.like(User::getUsername, baseRequestVO.getSearchKey())
                    .or()
                    .like(User::getPhoneNumber, baseRequestVO.getSearchKey())
                    .or()
                    .like(User::getEmail, baseRequestVO.getSearchKey()));
        }

        Page<User> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        Page<User> resultPage = lambdaQuery.orderByDesc(User::getCreateTime).page(page);

        // 转换为UserVO并设置isThirdPartyUser字段
        List<UserVO> userVOList = new ArrayList<>();
        List<User> records = resultPage.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            userVOList = records.stream().map(user -> {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);

                // 显式设置关键字段，确保数据正确传输
                userVO.setUserStatus(user.getUserStatus());
                userVO.setAdmire(user.getAdmire());
                userVO.setUserType(user.getUserType());

                // 设置是否为第三方登录用户
                userVO.setIsThirdPartyUser(user.getPlatformType() != null && !user.getPlatformType().trim().isEmpty());

                // 清除敏感信息
                userVO.setPassword(null);
                userVO.setOpenId(null);

                return userVO;
            }).collect(Collectors.toList());
        }

        // 创建UserVO的分页结果
        Page<UserVO> userVOPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        userVOPage.setRecords(userVOList);

        return PoetryResult.success(userVOPage);
    }

    @Override
    public PoetryResult<List<UserVO>> getUserByUsername(String username) {
        List<User> users = lambdaQuery().select(User::getId, User::getUsername, User::getAvatar, User::getGender, User::getIntroduction).like(User::getUsername, username).last("limit 5").list();
        List<UserVO> userVOS = users.stream().map(u -> {
            UserVO userVO = new UserVO();
            userVO.setId(u.getId());
            userVO.setUsername(u.getUsername());
            userVO.setAvatar(u.getAvatar());
            userVO.setIntroduction(u.getIntroduction());
            userVO.setGender(u.getGender());
            return userVO;
        }).collect(Collectors.toList());
        return PoetryResult.success(userVOS);
    }

    @Override
    public PoetryResult<UserVO> token(String userToken) {
        userToken = new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(userToken));

        if (!StringUtils.hasText(userToken)) {
            throw new PoetryRuntimeException("未登陆，请登陆后再进行操作！");
        }

        // 使用多级缓存策略获取用户信息
        User user = null;

        // 优先从UserCacheManager获取
        user = userCacheManager.getUserByToken(userToken);

        // 降级到Redis缓存获取
        if (user == null) {
            Integer userId = cacheService.getUserIdFromSession(userToken);
            if (userId != null) {
                user = cacheService.getCachedUser(userId);
            }
        }

        // 最后降级到PoetryCache获取
        if (user == null) {
            user = (User) PoetryCache.get(userToken);
        }

        if (user == null) {
            throw new PoetryRuntimeException("登录已过期，请重新登陆！");
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        userVO.setPassword(null);
        userVO.setUserType(user.getUserType());
        userVO.setAccessToken(userToken);

        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult<UserVO> subscribe(Integer labelId, Boolean flag) {
        UserVO userVO = null;
        User one = lambdaQuery().eq(User::getId, PoetryUtil.getUserId()).one();
        List<Integer> sub = JSON.parseArray(one.getSubscribe(), Integer.class);
        if (sub == null) sub = new ArrayList<>();
        if (flag) {
            if (!sub.contains(labelId)) {
                sub.add(labelId);
                User user = new User();
                user.setId(one.getId());
                user.setSubscribe(JSON.toJSONString(sub));
                updateById(user);

                userVO = new UserVO();
                BeanUtils.copyProperties(one, userVO);
                userVO.setPassword(null);
                userVO.setSubscribe(user.getSubscribe());
                userVO.setAccessToken(PoetryUtil.getToken());
            }
        } else {
            if (sub.contains(labelId)) {
                sub.remove(labelId);
                User user = new User();
                user.setId(one.getId());
                user.setSubscribe(JSON.toJSONString(sub));
                updateById(user);

                userVO = new UserVO();
                BeanUtils.copyProperties(one, userVO);
                userVO.setPassword(null);
                userVO.setSubscribe(user.getSubscribe());
                userVO.setAccessToken(PoetryUtil.getToken());
            }
        }
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult<UserVO> thirdLogin(String provider, String uid, String username, String email, String avatar) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(uid)) {
            return PoetryResult.fail("第三方登录信息不完整");
        }

        User existUser = lambdaQuery()
                .eq(User::getPlatformType, provider)
                .eq(User::getUid, uid)
                .one();

        if (existUser == null) {
            String finalUsername = username;
            if (!StringUtils.hasText(finalUsername)) {
                finalUsername = provider + "_user_" + System.currentTimeMillis();
            }

            int count = 0;
            String uniqueUsername = finalUsername;
            while (lambdaQuery().eq(User::getUsername, uniqueUsername).count() > 0) {
                uniqueUsername = finalUsername + "_" + (++count);
            }

            User newUser = new User();
            newUser.setUsername(uniqueUsername);
            newUser.setPlatformType(provider);
            newUser.setUid(uid);
            newUser.setEmail(email);
            newUser.setAvatar(avatar);
            newUser.setUserStatus(true);
            newUser.setUserType(PoetryEnum.USER_TYPE_USER.getCode());
            newUser.setGender(PoetryEnum.USER_GENDER_NONE.getCode());

            save(newUser);

            ImChatGroupUser imChatGroupUser = new ImChatGroupUser();
            imChatGroupUser.setGroupId(ImConfigConst.DEFAULT_GROUP_ID);
            imChatGroupUser.setUserId(newUser.getId());
            imChatGroupUser.setAdminFlag(false);
            imChatGroupUser.setUserStatus(ImConfigConst.GROUP_USER_STATUS_PASS);
            imChatGroupUserMapper.insert(imChatGroupUser);

            existUser = newUser;
        }

        String userToken = "";
        if (PoetryCache.get(CommonConst.USER_TOKEN + existUser.getId()) != null) {
            userToken = (String) PoetryCache.get(CommonConst.USER_TOKEN + existUser.getId());
        }

        if (!StringUtils.hasText(userToken)) {
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            userToken = CommonConst.USER_ACCESS_TOKEN + uuid;
            PoetryCache.put(userToken, existUser, CommonConst.TOKEN_EXPIRE);
            PoetryCache.put(CommonConst.USER_TOKEN + existUser.getId(), userToken, CommonConst.TOKEN_EXPIRE);
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(existUser, userVO);
        userVO.setPassword(null);
        userVO.setAccessToken(userToken);

        log.info("OAuth登录成功，用户: {} (ID: {})", existUser.getUsername(), existUser.getId());

        return PoetryResult.success(userVO);
    }

    private String getCodeMail(int i) {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        String webName = (webInfo == null ? "POETIZE" : webInfo.getWebName());
        return String.format(mailUtil.getMailText(),
                webName,
                String.format(MailUtil.imMail, PoetryUtil.getAdminUser().getUsername()),
                PoetryUtil.getAdminUser().getUsername(),
                String.format(codeFormat, i),
                "",
                webName);
    }

    @Override
    public PoetryResult getBindablePlatforms() {
        try {
            User currentUser = PoetryUtil.getCurrentUser();

            // 只有普通注册用户才能绑定第三方账号
            if (currentUser.getPlatformType() != null && !currentUser.getPlatformType().trim().isEmpty()) {
                return PoetryResult.fail("第三方登录用户不能绑定其他账号");
            }

            // 获取已启用且全局启用的第三方平台配置
            List<ThirdPartyOauthConfig> enabledConfigs = thirdPartyOauthConfigService.getActiveConfigs();

            // 转换为前端需要的格式
            List<Map<String, Object>> platforms = enabledConfigs.stream()
                .map(config -> {
                    Map<String, Object> platform = new HashMap<>();
                    platform.put("platformType", config.getPlatformType());
                    platform.put("platformName", getPlatformDisplayName(config.getPlatformType()));
                    platform.put("iconUrl", getPlatformIconUrl(config.getPlatformType()));
                    return platform;
                })
                .collect(Collectors.toList());

            return PoetryResult.success(platforms);
        } catch (Exception e) {
            log.error("获取可绑定平台列表失败", e);
            return PoetryResult.fail("获取平台列表失败");
        }
    }

    @Override
    public PoetryResult getAccountBindingStatus() {
        try {
            User currentUser = PoetryUtil.getCurrentUser();

            Map<String, Object> status = new HashMap<>();
            status.put("isThirdPartyUser", currentUser.getPlatformType() != null && !currentUser.getPlatformType().trim().isEmpty());
            status.put("boundPlatform", currentUser.getPlatformType());
            status.put("boundPlatformName", getPlatformDisplayName(currentUser.getPlatformType()));
            status.put("canBind", currentUser.getPlatformType() == null || currentUser.getPlatformType().trim().isEmpty());

            return PoetryResult.success(status);
        } catch (Exception e) {
            log.error("获取账号绑定状态失败", e);
            return PoetryResult.fail("获取绑定状态失败");
        }
    }

    @Override
    public PoetryResult getOAuthAuthUrl(String platformType) {
        try {
            User currentUser = PoetryUtil.getCurrentUser();

            // 检查是否为普通注册用户
            if (currentUser.getPlatformType() != null && !currentUser.getPlatformType().trim().isEmpty()) {
                return PoetryResult.fail("第三方登录用户不能绑定其他账号");
            }

            // 检查平台是否已配置
            if (!oAuthClientService.isPlatformConfigured(platformType)) {
                return PoetryResult.fail("平台未配置或未启用");
            }

            // 生成state参数（简单随机字符串，绑定功能已移除）
            String state = java.util.UUID.randomUUID().toString();

            // 构建授权URL
            String authUrl = oAuthClientService.buildAuthUrl(platformType, state);

            Map<String, Object> result = new HashMap<>();
            result.put("authUrl", authUrl);
            result.put("platformType", platformType);
            result.put("state", state);

            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("获取OAuth授权URL失败: platformType={}", platformType, e);
            return PoetryResult.fail("获取授权URL失败：" + e.getMessage());
        }
    }

    @Override
    public PoetryResult bindThirdPartyAccount(String platformType, String code, String state) {
        try {
            log.info("开始绑定第三方账号: platformType={}, code={}, state={}", platformType, code, state);

            // 绑定功能已移除，直接返回错误
            log.error("绑定功能已移除");
            return PoetryResult.fail("绑定功能暂不可用");


        } catch (Exception e) {
            log.error("绑定第三方账号失败: platformType={}, code={}", platformType, code, e);
            return PoetryResult.fail("绑定失败：" + e.getMessage());
        }
    }

    @Override
    public PoetryResult unbindThirdPartyAccount() {
        try {
            User currentUser = PoetryUtil.getCurrentUser();

            // 检查是否已绑定第三方账号
            if (currentUser.getPlatformType() == null || currentUser.getPlatformType().trim().isEmpty()) {
                return PoetryResult.fail("您还没有绑定第三方账号");
            }

            // 解绑第三方账号
            currentUser.setPlatformType(null);
            currentUser.setUid(null);

            boolean success = updateById(currentUser);
            if (success) {
                return PoetryResult.success("解绑成功");
            } else {
                return PoetryResult.fail("解绑失败");
            }
        } catch (Exception e) {
            log.error("解绑第三方账号失败", e);
            return PoetryResult.fail("解绑失败");
        }
    }

    /**
     * 获取平台显示名称
     */
    private String getPlatformDisplayName(String platformType) {
        if (platformType == null) {
            return null;
        }
        switch (platformType.toLowerCase()) {
            case "github":
                return "GitHub";
            case "google":
                return "Google";
            case "x":
            case "twitter":
                return "Twitter/X";
            case "yandex":
                return "Yandex";
            case "gitee":
                return "Gitee";
            default:
                return platformType;
        }
    }

    /**
     * 获取平台图标URL
     */
    private String getPlatformIconUrl(String platformType) {
        if (platformType == null) {
            return null;
        }
        return "/static/svg/" + platformType.toLowerCase() + ".svg";
    }
}