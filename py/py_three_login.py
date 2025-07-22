import httpx
import os
import secrets
from urllib.parse import parse_qs, urlencode, quote
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests
from oauthlib.oauth1 import Client
from fastapi import FastAPI, Request, HTTPException, Depends
from fastapi.responses import RedirectResponse, JSONResponse
from starlette.middleware.sessions import SessionMiddleware
from config import SECRET_KEY, JAVA_BACKEND_URL, FRONTEND_URL, JAVA_CONFIG_URL, BASE_BACKEND_URL
from redis_oauth_state_manager import oauth_state_manager, get_session_id
from cache_service import get_cache_service

# 定义Yandex OAuth实现
class YandexOAuth:
    @staticmethod
    def get_session():
        return None  # 简化实现，不需要session对象
        
# 创建Yandex替代模块
yandex = YandexOAuth()

# 修复导入路径
ENV = os.environ.get("ENV", "development")
from web_admin_api import get_third_login_config

# 创建独立运行使用的FastAPI应用
app = FastAPI()
app.add_middleware(SessionMiddleware, secret_key=SECRET_KEY)

# ============================
# 工具函数
# ============================
def generate_state_token():
    """生成防CSRF的随机state"""
    return secrets.token_urlsafe(16)

def get_oauth_config(provider):
    try:
        # 从本地配置文件中获取第三方登录配置
        config = get_third_login_config()
        if not config:
            print(f"无法获取第三方登录配置")
            return None
        
        # 检查第三方登录功能是否启用
        if not config.get('enable', False):
            print(f"第三方登录功能未启用")
            return None
        
        # 获取特定提供商的配置
        provider_config = config.get(provider)
        if not provider_config:
            print(f"未找到{provider}的配置")
            return None
        
        # 检查该平台是否启用
        if not provider_config.get('enabled', True):
            print(f"{provider}平台登录功能未启用")
            return None
        
        # 检查配置是否完整
        if provider == 'twitter':
            if not (provider_config.get('client_key') and provider_config.get('client_secret')):
                print(f"{provider}配置不完整")
                return None
        else:
            if not (provider_config.get('client_id') and provider_config.get('client_secret')):
                print(f"{provider}配置不完整")
                return None
        
        return provider_config
    except Exception as e:
        print(f"获取{provider}配置时出错: {str(e)}")
        return None

# ============================
# 第三方登录配置
# ============================
def get_github_config():
    configs = get_oauth_config("github")
    if not configs:
        return {
            "client_id": "",
            "client_secret": "",
            "auth_url": "https://github.com/login/oauth/authorize",
            "token_url": "https://github.com/login/oauth/access_token",
            "user_info_url": "https://api.github.com/user",
            "emails_url": "https://api.github.com/user/emails",
            "redirect_uri": "",
            "scope": "user:email"
        }
    
    github_config = configs
    return {
        "client_id": github_config.get("client_id", ""),
        "client_secret": github_config.get("client_secret", ""),
        "auth_url": "https://github.com/login/oauth/authorize",
        "token_url": "https://github.com/login/oauth/access_token",
        "user_info_url": "https://api.github.com/user",
        "emails_url": "https://api.github.com/user/emails",
        "redirect_uri": github_config.get("redirect_uri", ""),
        "scope": "user:email"
    }

def get_google_config():
    configs = get_oauth_config("google")
    if not configs:
        return {
            "client_id": "",
            "client_secret": "",
            "auth_url": "https://accounts.google.com/o/oauth2/v2/auth",
            "token_url": "https://oauth2.googleapis.com/token",
            "user_info_url": "https://people.googleapis.com/v1/people/me",
            "redirect_uri": "",
            "scope": "openid email profile"
        }
    
    google_config = configs
    return {
        "client_id": google_config.get("client_id", ""),
        "client_secret": google_config.get("client_secret", ""),
        "auth_url": "https://accounts.google.com/o/oauth2/v2/auth",
        "token_url": "https://oauth2.googleapis.com/token",
        "user_info_url": "https://people.googleapis.com/v1/people/me",
        "redirect_uri": google_config.get("redirect_uri", ""),
        "scope": "openid email profile"
    }

def get_twitter_config():
    configs = get_oauth_config("x")
    if not configs:
        return {
            "client_key": "",
            "client_secret": "",
            "request_token_url": "https://api.twitter.com/oauth/request_token",
            "auth_url": "https://api.twitter.com/oauth/authenticate",
            "access_token_url": "https://api.twitter.com/oauth/access_token",
            "user_info_url": "https://api.twitter.com/1.1/account/verify_credentials.json",
            "redirect_uri": "",
            "include_email": "true"
        }
    
    twitter_config = configs
    return {
        "client_key": twitter_config.get("client_key", ""),
        "client_secret": twitter_config.get("client_secret", ""),
        "request_token_url": "https://api.twitter.com/oauth/request_token",
        "auth_url": "https://api.twitter.com/oauth/authenticate",
        "access_token_url": "https://api.twitter.com/oauth/access_token",
        "user_info_url": "https://api.twitter.com/1.1/account/verify_credentials.json",
        "redirect_uri": twitter_config.get("redirect_uri", ""),
        "include_email": "true"
    }

def get_yandex_config():
    configs = get_oauth_config("yandex")
    if not configs:
        return {
            "client_id": "",
            "client_secret": "",
            "auth_url": "https://oauth.yandex.com/authorize",
            "token_url": "https://oauth.yandex.com/token",
            "user_info_url": "https://login.yandex.ru/info",
            "redirect_uri": "",
            "scope": "login:email login:info"
        }
    
    yandex_config = configs
    return {
        "client_id": yandex_config.get("client_id", ""),
        "client_secret": yandex_config.get("client_secret", ""),
        "auth_url": "https://oauth.yandex.com/authorize",
        "token_url": "https://oauth.yandex.com/token",
        "user_info_url": "https://login.yandex.ru/info",
        "redirect_uri": yandex_config.get("redirect_uri", ""),
        "scope": "login:email login:info"
    }

def get_gitee_config():
    configs = get_oauth_config("gitee")
    if not configs:
        return {
            "client_id": "",
            "client_secret": "",
            "auth_url": "https://gitee.com/oauth/authorize",
            "token_url": "https://gitee.com/oauth/token",
            "user_info_url": "https://gitee.com/api/v5/user",
            "redirect_uri": "",
            "scope": "user_info emails"
        }
    
    gitee_config = configs
    return {
        "client_id": gitee_config.get("client_id", ""),
        "client_secret": gitee_config.get("client_secret", ""),
        "auth_url": "https://gitee.com/oauth/authorize",
        "token_url": "https://gitee.com/oauth/token",
        "user_info_url": "https://gitee.com/api/v5/user",
        "redirect_uri": gitee_config.get("redirect_uri", ""),
        "scope": "user_info emails"
    }

# ============================
# 邮箱检测工具函数
# ============================
def check_email_collection_needed(email, provider):
    """
    检查是否需要前端收集邮箱

    Args:
        email: 从OAuth API获取的邮箱地址
        provider: OAuth提供商名称

    Returns:
        tuple: (processed_email, email_collection_needed)
    """
    # 检查邮箱是否为空或无效
    if not email or email.strip() == "":
        print(f"{provider}用户未绑定邮箱，需要前端收集")
        return "", True

    # 邮箱存在且有效
    return email.strip(), False

# ============================
# 路由定义
# ============================
async def oauth_login(provider: str, request: Request):
    """统一登录入口"""
    print(f"启动 {provider} OAuth登录")

    # 检查session是否可用
    try:
        test_session = request.session
    except Exception as e:
        print(f"Session中间件错误: {e}")
        return JSONResponse({"error": "Session middleware not available"}, status_code=500)

    config = None
    if provider == "github":
        config = get_github_config()
    elif provider == "google":
        config = get_google_config()
    elif provider == "x":
        config = get_twitter_config()
    elif provider == "yandex":
        config = get_yandex_config()
    elif provider == "gitee":
        config = get_gitee_config()

    if not config:
        print(f"{provider} OAuth配置未找到")
        return JSONResponse({"error": "Unsupported provider"}, status_code=400)
    
    # 检查配置有效性
    if provider != "x" and (not config.get("client_id") or not config.get("client_secret")):
        return JSONResponse({"error": "未配置OAuth信息，请先在后台设置"}, status_code=400)
    elif provider == "x" and (not config.get("client_key") or not config.get("client_secret")):
        return JSONResponse({"error": "未配置OAuth信息，请先在后台设置"}, status_code=400)

    try:
        # Twitter OAuth 1.0 特殊处理
        if provider == "x":
            client = Client(config["client_key"], config["client_secret"])
            uri, headers, body = client.sign(
                config["request_token_url"],
                http_method="POST",
                callback_uri=config["redirect_uri"]
            )
            async with httpx.AsyncClient() as client:
                response = await client.post(uri, headers=headers, data=body)
            if response.status_code != 200:
                return JSONResponse({"error": "Twitter request token failed"}, status_code=500)

            request_token = parse_qs(response.text)
            oauth_token = request_token.get("oauth_token", [None])[0]
            request.session["x_oauth_token_secret"] = request_token.get("oauth_token_secret", [None])[0]

            auth_url = f"{config['auth_url']}?oauth_token={oauth_token}"
            return RedirectResponse(auth_url)

        # OAuth 2.0 平台处理 - 使用改进的状态管理
        session_id = get_session_id(request)
        state = oauth_state_manager.generate_state(provider, session_id)

        # 同时存储到session作为备份（如果session可用）
        try:
            request.session[f"{provider}_state"] = state
        except Exception as e:
            print(f"无法存储到session，使用状态管理器: {e}")

        auth_params = {
            "client_id": config["client_id"],
            "redirect_uri": config["redirect_uri"],
            "scope": config.get("scope", ""),
            "state": state,
            "response_type": "code"
        }
        if provider == "google":
            auth_params["access_type"] = "offline"  # 获取refresh_token

        auth_url = config["auth_url"] + "?" + urlencode(auth_params)
        return RedirectResponse(auth_url)

    except Exception as e:
        print(f"Login init failed: {str(e)}")
        return JSONResponse({"error": "Service unavailable"}, status_code=500)

def determine_action_type_from_state_info(state_info: dict) -> str:
    """
    从状态信息中判断操作类型（绑定 vs 登录）
    """
    if state_info:
        action = state_info.get("action", "login")
        print(f"✅ 从状态信息获取操作类型: {action}")
        return action
    else:
        print("⚠️ 状态信息为空，默认为登录操作")
        return "login"

def get_state_info_before_validation(state: str, expected_provider: str = None) -> dict:
    """
    安全地获取OAuth状态信息（不消费state token）
    用于在验证前确定操作类型，但不删除state以保证后续验证的完整性

    Args:
        state: OAuth状态token
        expected_provider: 期望的OAuth提供商，用于防止provider混淆攻击

    Returns:
        dict: 状态信息，如果验证失败则返回None
    """
    import logging
    logger = logging.getLogger(__name__)

    try:
        if not state:
            logger.warning("OAuth回调缺少state参数，可能存在CSRF攻击风险")
            return None

        # 从Redis OAuth状态管理器安全地获取状态信息
        state_data = oauth_state_manager.get_state_info(state)

        if not state_data:
            logger.warning(f"OAuth状态不存在或已过期: state={state[:8]}***{state[-4:] if len(state) > 12 else '***'}")
            return None

        # 验证状态数据的完整性
        if not isinstance(state_data, dict):
            logger.error(f"OAuth状态数据格式错误: type={type(state_data)}")
            return None

        # 检查必要字段
        stored_provider = state_data.get('provider')
        if not stored_provider:
            logger.error("OAuth状态数据缺少provider字段")
            return None

        # 🔒 关键安全检查：验证provider匹配，防止CSRF攻击
        if expected_provider and stored_provider != expected_provider:
            logger.warning(f"🚨 检测到潜在的CSRF攻击：OAuth provider不匹配！")
            logger.warning(f"   期望provider: {expected_provider}")
            logger.warning(f"   状态中的provider: {stored_provider}")
            logger.warning(f"   state token: {state[:8]}***{state[-4:] if len(state) > 12 else '***'}")
            logger.warning(f"   这可能是攻击者尝试使用其他provider的state token进行CSRF攻击")
            return None

        # 检查过期时间（如果存在）
        expires_at = state_data.get('expires_at')
        if expires_at:
            import time
            current_time = time.time()
            if current_time > expires_at:
                logger.warning(f"OAuth状态已过期: provider={stored_provider}, expired_at={expires_at}")
                return None

        # 安全地记录状态信息获取成功
        logger.info(f"OAuth状态验证通过: provider={stored_provider}, state={state[:8]}***")

        # 返回包含操作类型的状态信息，默认为登录操作
        # 注意：这里不删除state，保留给后续的正式验证流程
        return {
            "action": state_data.get("action", "login"),  # 从状态中获取真实的操作类型
            "provider": stored_provider,
            "session_id": state_data.get("session_id"),
            "timestamp": state_data.get("timestamp"),
            "created_at": state_data.get("created_at")
        }

    except Exception as e:
        logger.error(f"获取OAuth状态信息时发生异常: {str(e)}")
        return None

def should_delete_state_after_validation(action_type: str) -> bool:
    """
    根据操作类型决定是否在验证后删除状态token

    Args:
        action_type: 操作类型 ("bind" 或 "login")

    Returns:
        bool: 是否删除状态token
    """
    import logging
    logger = logging.getLogger(__name__)

    if action_type == "bind":
        # 绑定操作：不删除状态token，让Java后端处理
        logger.info("绑定操作：保留状态token供Java后端验证")
        return False
    else:
        # 登录操作：删除状态token（一次性使用）
        logger.info("登录操作：验证后删除状态token")
        return True

def secure_validate_oauth_state(state: str, provider: str, action_type: str = "login") -> dict:
    """
    安全地验证OAuth状态token

    Args:
        state: OAuth状态token
        provider: OAuth提供商
        action_type: 操作类型 ("bind" 或 "login")

    Returns:
        dict: 验证结果，包含success字段和相关信息
    """
    import logging
    logger = logging.getLogger(__name__)

    try:
        if not state:
            logger.warning(f"OAuth状态验证失败: 缺少state参数 - provider={provider}")
            return {
                "success": False,
                "error": "missing_state",
                "message": "缺少必要的安全验证参数"
            }

        if not provider:
            logger.warning(f"OAuth状态验证失败: 缺少provider参数 - state={state[:8]}***")
            return {
                "success": False,
                "error": "missing_provider",
                "message": "缺少OAuth提供商信息"
            }

        # 根据操作类型决定是否消费state token
        if should_delete_state_after_validation(action_type):
            # 登录操作：验证并消费state（一次性使用）
            state_data = oauth_state_manager.verify_and_consume_state(state, provider)
        else:
            # 绑定操作：只验证不消费（让Java后端处理）
            state_data = oauth_state_manager.get_state_info(state)
            if state_data and state_data.get('provider') != provider:
                logger.warning(f"🚨 检测到潜在的CSRF攻击：OAuth provider不匹配！")
                logger.warning(f"   期望provider: {provider}")
                logger.warning(f"   状态中的provider: {state_data.get('provider')}")
                logger.warning(f"   state token: {state[:8]}***{state[-4:] if len(state) > 12 else '***'}")
                state_data = None

        if not state_data:
            logger.warning(f"OAuth状态验证失败: state无效或已过期 - provider={provider}, state={state[:8]}***")
            return {
                "success": False,
                "error": "invalid_state",
                "message": "安全验证失败，请重新授权"
            }

        logger.info(f"OAuth状态验证成功: provider={provider}, action={action_type}, state={state[:8]}***")
        return {
            "success": True,
            "state_data": state_data,
            "provider": provider,
            "action_type": action_type
        }

    except Exception as e:
        logger.error(f"OAuth状态验证异常: provider={provider}, action={action_type}, error={str(e)}")
        return {
            "success": False,
            "error": "validation_exception",
            "message": "状态验证过程中发生错误"
        }



async def call_java_bind_api_direct(provider: str, code: str, state: str, state_info: dict):
    """
    直接调用Java后端的绑定接口，避免授权码过期
    优化版本：减少超时时间，快速失败
    """
    try:
        print(f"🔗 直接调用Java绑定接口: provider={provider}, code={code[:10]}..., state={state[:10]}...")

        # 从状态信息中获取用户ID用于日志记录
        user_id = state_info.get("userId") if state_info else None
        print(f"📋 状态信息: userId={user_id}, action={state_info.get('action') if state_info else 'unknown'}")

        # 优化的请求头和超时配置
        headers = {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Internal-Service': 'poetize-python',
            'User-Agent': 'poetize-python/1.0.0'
        }

        print(f"🚀 发送绑定请求到Java后端")

        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                f"{JAVA_BACKEND_URL}/user/bindThirdPartyAccount",
                data={
                    "platformType": provider,
                    "code": code,
                    "state": state
                },
                headers=headers
            )

        print(f"✅ Java绑定接口响应: status={response.status_code}")
        return response

    except httpx.RequestError as e:
        print(f"API请求失败: {str(e)}")
        # 创建一个模拟的错误响应
        class MockResponse:
            def __init__(self, status_code, data):
                self.status_code = status_code
                self._json_data = data

            def json(self):
                return self._json_data

        return MockResponse(502, {"code": 502, "message": "第三方服务不可用"})

async def call_java_login_api(unified_data: dict):
    """调用Java后端的登录接口"""
    try:
        print(f"🔑 调用Java登录接口: provider={unified_data.get('provider')}")

        async with httpx.AsyncClient() as client:
            headers = {
                'Content-Type': 'application/json',
                'X-Internal-Service': 'poetize-python',
                'User-Agent': 'poetize-python/1.0.0'
            }
            response = await client.post(
                f"{JAVA_BACKEND_URL}/oauth/callback",
                json=unified_data,
                headers=headers,
                timeout=5
            )

        print(f"✅ Java登录接口响应: status={response.status_code}")
        return response

    except Exception as e:
        print(f"❌ 调用Java登录接口失败: {e}")
        # 创建一个模拟的错误响应
        class MockResponse:
            def __init__(self, status_code, data):
                self.status_code = status_code
                self._json_data = data

            def json(self):
                return self._json_data

        return MockResponse(500, {"code": 500, "message": f"登录失败: {str(e)}"})

async def oauth_callback(provider: str, request: Request):
    """统一OAuth回调处理 - 使用安全的state验证机制"""
    import logging
    logger = logging.getLogger(__name__)

    logger.info(f"开始处理OAuth回调: provider={provider}")

    # 获取OAuth参数
    code = request.query_params.get("code")
    state = request.query_params.get("state")
    error = request.query_params.get("error")

    # 检查OAuth错误
    if error:
        logger.warning(f"OAuth授权失败: provider={provider}, error={error}")
        return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error={error}&platform={provider}")

    # 🔒 在状态验证前先安全地获取操作类型，并验证provider匹配
    state_info = get_state_info_before_validation(state, provider)
    action_type = determine_action_type_from_state_info(state_info)

    logger.info(f"检测到操作类型: provider={provider}, action={action_type}")

    # 执行安全的state验证
    validation_result = secure_validate_oauth_state(state, provider, action_type)
    if not validation_result["success"]:
        error_code = validation_result.get("error", "unknown")
        error_message = validation_result.get("message", "状态验证失败")

        logger.warning(f"OAuth状态验证失败: provider={provider}, error={error_code}, message={error_message}")

        # 返回安全的错误信息，不泄露具体的验证失败原因
        return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error=state_validation_failed&platform={provider}")

    logger.info(f"OAuth状态验证成功: provider={provider}, action={action_type}")

    # 检查session状态（保持向后兼容）
    try:
        dict(request.session)
    except Exception as e:
        logger.error(f"Session访问错误: provider={provider}, error={str(e)}")
        return JSONResponse({"error": "Session error"}, status_code=500)

    config = None
    if provider == "github":
        config = get_github_config()
    elif provider == "google":
        config = get_google_config()
    elif provider == "x":
        config = get_twitter_config()
    elif provider == "yandex":
        config = get_yandex_config()
    elif provider == "gitee":
        config = get_gitee_config()

    if not config:
        print(f"{provider} OAuth配置未找到")
        return JSONResponse({"error": "Unsupported provider"}, status_code=400)

    try:
        # Twitter OAuth 1.0 处理
        if provider == "x":
            oauth_token = request.query_params.get("oauth_token")
            oauth_verifier = request.query_params.get("oauth_verifier")
            oauth_token_secret = request.session.get("x_oauth_token_secret")

            if not all([oauth_token, oauth_verifier, oauth_token_secret]):
                return JSONResponse({"error": "Invalid parameters"}, status_code=400)

            client = Client(
                config["client_key"],
                config["client_secret"],
                resource_owner_key=oauth_token,
                resource_owner_secret=oauth_token_secret,
                verifier=oauth_verifier
            )
            uri, headers, body = client.sign(config["access_token_url"], http_method="POST")
            async with httpx.AsyncClient() as client:
                response = await client.post(uri, headers=headers, data=body)
            access_data = parse_qs(response.text)
            access_token = access_data.get("oauth_token", [None])[0]
            access_token_secret = access_data.get("oauth_token_secret", [None])[0]

            # 获取用户信息（带邮箱）
            auth_client = Client(
                config["client_key"],
                config["client_secret"],
                resource_owner_key=access_token,
                resource_owner_secret=access_token_secret
            )
            user_info_url = f"{config['user_info_url']}?include_email=true"
            async with httpx.AsyncClient() as client:
                uri, headers, body = auth_client.sign(user_info_url)
                user_response = await client.get(uri, headers=headers)
            user_info = user_response.json()

            # 检查是否需要前端收集邮箱
            raw_email = user_info.get("email")
            processed_email, email_collection_needed = check_email_collection_needed(raw_email, "Twitter/X")

            unified_data = {
                "provider": "x",
                "uid": user_info.get("id_str"),
                "username": user_info.get("screen_name"),
                "email": processed_email,
                "avatar": user_info.get("profile_image_url_https", "").replace("_normal", ""),
                "email_collection_needed": email_collection_needed
            }

        # Yandex 处理
        elif provider == "yandex":
            if not code:
                logger.warning(f"Yandex OAuth错误: 缺少授权码")
                return JSONResponse({"error": "Missing authorization code"}, status_code=400)

            # state验证已在函数开始时完成，这里直接处理授权码

            async with httpx.AsyncClient() as client:
                token_response = await client.post(
                    config["token_url"],
                    data={
                        "grant_type": "authorization_code",
                        "code": code,
                        "client_id": config["client_id"],
                        "client_secret": config["client_secret"],
                        "redirect_uri": config["redirect_uri"]
                    }
                )
            token_data = token_response.json()
            access_token = token_data.get("access_token")

            async with httpx.AsyncClient() as client:
                user_response = await client.get(
                    config["user_info_url"],
                    params={"format": "json"},
                    headers={"Authorization": f"OAuth {access_token}"}
                )
            user_info = user_response.json()

            # 检查是否需要前端收集邮箱
            raw_email = user_info.get("default_email")
            processed_email, email_collection_needed = check_email_collection_needed(raw_email, "Yandex")

            unified_data = {
                "provider": "yandex",
                "uid": user_info.get("id"),
                "username": user_info.get("login"),
                "email": processed_email,
                "avatar": f"https://avatars.yandex.net/get-yapic/{user_info.get('default_avatar_id')}/islands-200",
                "email_collection_needed": email_collection_needed
            }

        # GitHub 处理
        elif provider == "github":
            if not code:
                logger.warning(f"GitHub OAuth错误: 缺少授权码")
                return JSONResponse({"error": "Missing authorization code"}, status_code=400)

            # state验证已在函数开始时完成，这里直接处理授权码

            async with httpx.AsyncClient() as client:
                token_response = await client.post(
                    config["token_url"],
                    headers={"Accept": "application/json"},
                    data={
                        "client_id": config["client_id"],
                        "client_secret": config["client_secret"],
                        "code": code,
                        "redirect_uri": config["redirect_uri"]
                    }
                )
            access_token = token_response.json().get("access_token")

            async with httpx.AsyncClient() as client:
                user_info_response = await client.get(
                    config["user_info_url"],
                    headers={"Authorization": f"token {access_token}"}
                )
                user_info = user_info_response.json()

                emails_response = await client.get(
                    config["emails_url"],
                    headers={"Authorization": f"token {access_token}"}
                )
                emails = emails_response.json()

            primary_email = next((e["email"] for e in emails if e["primary"] and e["verified"]), None)

            # 检查是否需要前端收集邮箱
            processed_email, email_collection_needed = check_email_collection_needed(primary_email, "GitHub")

            # 确保所有字段都是字符串类型，避免Java端类型转换问题
            unified_data = {
                "provider": "github",
                "uid": str(user_info.get("id", "")),
                "username": user_info.get("login", ""),
                "email": processed_email,
                "avatar": user_info.get("avatar_url", ""),
                "email_collection_needed": email_collection_needed
            }

        # Google 处理
        elif provider == "google":
            if not code:
                logger.warning(f"Google OAuth错误: 缺少授权码")
                return JSONResponse({"error": "Missing authorization code"}, status_code=400)

            # state验证已在函数开始时完成，这里直接处理授权码

            async with httpx.AsyncClient() as client:
                token_response = await client.post(
                    config["token_url"],
                    data={
                        "code": code,
                        "client_id": config["client_id"],
                        "client_secret": config["client_secret"],
                        "redirect_uri": config["redirect_uri"],
                        "grant_type": "authorization_code"
                    }
                )
            token_data = token_response.json()
            access_token = token_data.get("access_token")
            id_token_jwt = token_data.get("id_token")

            # 验证ID Token
            id_info = id_token.verify_oauth2_token(
                id_token_jwt,
                google_requests.Request(),
                config["client_id"]
            )
            if id_info['iss'] not in ['accounts.google.com', 'https://accounts.google.com']:
                raise ValueError("Invalid issuer")

            async with httpx.AsyncClient() as client:
                user_response = await client.get(
                    config["user_info_url"],
                    params={"personFields": "names,emailAddresses,photos"},
                    headers={"Authorization": f"Bearer {access_token}"}
                )
            user_data = user_response.json()

            # 检查是否需要前端收集邮箱
            raw_email = id_info.get("email", "")
            processed_email, email_collection_needed = check_email_collection_needed(raw_email, "Google")

            # 确保所有字段都是字符串类型
            unified_data = {
                "provider": "google",
                "uid": str(id_info.get("sub", "")),
                "username": user_data.get("names", [{}])[0].get("displayName", ""),
                "email": processed_email,
                "avatar": user_data.get("photos", [{}])[0].get("url", ""),
                "email_collection_needed": email_collection_needed
            }

        # Gitee 处理
        elif provider == "gitee":
            if not code:
                logger.warning(f"Gitee OAuth错误: 缺少授权码")
                return JSONResponse({"error": "Missing authorization code"}, status_code=400)

            # state验证已在函数开始时完成，这里直接处理授权码

            async with httpx.AsyncClient() as client:
                token_response = await client.post(
                    config["token_url"],
                    data={
                        "client_id": config["client_id"],
                        "client_secret": config["client_secret"],
                        "code": code,
                        "grant_type": "authorization_code",
                        "redirect_uri": config["redirect_uri"]
                    }
                )
            token_data = token_response.json()
            access_token = token_data.get("access_token")

            # 获取用户基本信息
            async with httpx.AsyncClient() as client:
                user_response = await client.get(
                    config["user_info_url"],
                    headers={"Authorization": f"token {access_token}"}
                )
            user_info = user_response.json()

            # 获取用户邮箱信息（Gitee需要单独调用邮箱API）
            user_email = ""
            try:
                async with httpx.AsyncClient() as client:
                    emails_response = await client.get(
                        "https://gitee.com/api/v5/emails",
                        headers={"Authorization": f"token {access_token}"}
                    )

                if emails_response.status_code == 200:
                    emails_data = emails_response.json()

                    # 优先选择主邮箱
                    primary_email = None
                    verified_email = None

                    for email_info in emails_data:
                        if email_info.get("primary", False):
                            primary_email = email_info.get("email", "")
                        elif email_info.get("verified", False) and not verified_email:
                            verified_email = email_info.get("email", "")

                    # 选择邮箱优先级：主邮箱 > 已验证邮箱 > 第一个邮箱
                    if primary_email:
                        user_email = primary_email
                    elif verified_email:
                        user_email = verified_email
                    elif emails_data and len(emails_data) > 0:
                        user_email = emails_data[0].get("email", "")
                else:
                    print(f"Gitee邮箱API请求失败: HTTP {emails_response.status_code}")
            except Exception as e:
                print(f"获取Gitee邮箱信息异常: {e}")

            # 检查是否需要前端收集邮箱
            processed_email, email_collection_needed = check_email_collection_needed(user_email, "Gitee")

            # 确保所有字段都是字符串类型
            unified_data = {
                "provider": "gitee",
                "uid": str(user_info.get("id", "")),
                "username": user_info.get("login", ""),
                "email": processed_email,
                "avatar": user_info.get("avatar_url", ""),
                "email_collection_needed": email_collection_needed
            }

        else:
            return JSONResponse({"error": "Unsupported provider"}, status_code=400)

        # 使用之前获取的操作类型
        print(f"🎯 使用操作类型: {action_type}")

        if action_type == "bind":
            # 绑定操作：立即调用Java后端绑定接口，避免授权码过期
            # 跳过Python端的用户信息获取，减少延迟
            # code、state、error参数已在函数开始时获取

            if error:
                logger.warning(f"{provider} OAuth授权失败: {error}")
                return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error={error}&platform={provider}")
            elif code and state:
                logger.info(f"{provider} OAuth授权成功，立即调用Java绑定接口（跳过Python用户信息获取）")

                # 记录时间戳，用于分析时序
                import time
                start_time = time.time()
                logger.info(f"开始调用Java绑定接口: provider={provider}, timestamp={start_time}")

                # 立即调用Java后端绑定接口，避免授权码过期
                java_response = await call_java_bind_api_direct(provider, code, state, state_info)

                end_time = time.time()
                elapsed_time = end_time - start_time
                logger.info(f"Java绑定接口调用完成: provider={provider}, 耗时={elapsed_time:.2f}秒")

                # 解析Java响应
                try:
                    response_data = java_response.json()
                    logger.info(f"Java响应数据: provider={provider}, status={java_response.status_code}, success={response_data.get('code') == 200}")
                except Exception as json_error:
                    logger.error(f"解析Java响应JSON失败: provider={provider}, error={json_error}")
                    logger.error(f"原始响应: status={java_response.status_code}, content={getattr(java_response, 'text', 'N/A')}")
                    return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error=Java后端响应格式错误&platform={provider}")

                if java_response.status_code == 200 and response_data.get("code") == 200:
                    logger.info(f"{provider} 账号绑定成功，总耗时: {elapsed_time:.2f}秒")
                    return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?success=true&platform={provider}&message=绑定成功")
                else:
                    error_message = response_data.get("message", "绑定失败")
                    logger.warning(f"{provider} 账号绑定失败: {error_message}，总耗时: {elapsed_time:.2f}秒")
                    logger.warning(f"失败详情: Java状态码={java_response.status_code}, 业务状态码={response_data.get('code')}")
                    return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error={error_message}&platform={provider}")
            else:
                print(f"❌ {provider} OAuth回调参数不完整")
                return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error=授权参数不完整&platform={provider}")
        else:
            # 登录操作：调用登录接口
            java_response = await call_java_login_api(unified_data)

            # 解析Java响应
            response_data = java_response.json()

            # 登录操作的响应处理
            if java_response.status_code == 200 and response_data.get("code") == 200:
                user_data = response_data.get("data", {})
                access_token = user_data.get("accessToken")
                response_message = response_data.get("message", "")

                if access_token:
                    # 检查是否需要邮箱收集
                    if response_message == "EMAIL_COLLECTION_NEEDED":
                        print(f"{provider} OAuth成功，需要邮箱收集")
                        # 重定向到前端，并添加邮箱收集标记
                        return RedirectResponse(f"{FRONTEND_URL}?userToken={access_token}&emailCollectionNeeded=true")
                    else:
                        print(f"{provider} OAuth成功")
                        # 正常的OAuth登录重定向
                        return RedirectResponse(f"{FRONTEND_URL}?userToken={access_token}")

            # 登录失败，返回原始响应
            return JSONResponse(response_data)

    except httpx.TimeoutException as e:
        logger.error(f"Java后端调用超时: provider={provider}, error={str(e)}")
        return JSONResponse({"error": "服务响应超时，请稍后重试"}, status_code=504)

    except httpx.ConnectError as e:
        logger.error(f"Java后端连接失败: provider={provider}, url={JAVA_BACKEND_URL}, error={str(e)}")
        return JSONResponse({"error": "服务暂时不可用，请稍后重试"}, status_code=502)

    except httpx.HTTPStatusError as e:
        logger.error(f"Java后端HTTP错误: provider={provider}, status={e.response.status_code}")
        return JSONResponse({"error": "服务处理错误，请稍后重试"}, status_code=502)

    except httpx.RequestError as e:
        logger.error(f"HTTP请求异常: provider={provider}, error={str(e)}")
        return JSONResponse({"error": "网络请求失败，请检查网络连接"}, status_code=502)

    except ValueError as e:
        logger.error(f"数据解析失败: provider={provider}, error={str(e)}")
        return JSONResponse({"error": "数据格式错误，请重新授权"}, status_code=400)

    except Exception as e:
        logger.error(f"OAuth回调处理失败: provider={provider}, error={str(e)}")
        return JSONResponse({"error": "OAuth回调处理失败，请重新授权"}, status_code=500)

# 注册第三方登录API到FastAPI应用
def register_third_login_api(app: FastAPI):
    """注册第三方登录相关API"""
    
    @app.get('/login/{provider}')
    async def login_route(provider: str, request: Request):
        return await oauth_login(provider, request)
    
    @app.get('/callback/{provider}')
    async def callback_route(provider: str, request: Request):
        return await oauth_callback(provider, request)
    
    @app.get('/health')
    async def health_check():
        """健康检查接口"""
        return {"status": "ok", "service": "third-party-login-service"}

# 当作为独立模块运行时
if __name__ == '__main__':
    import uvicorn
    from web_admin_api import register_web_admin_api
    
    # 注册网站管理API
    register_web_admin_api(app)
    
    # 注册第三方登录路由
    register_third_login_api(app)
    
    # 启动服务
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("ENV") == "development"
    print(f"启动第三方登录服务，端口: {port}，调试模式: {debug}")
    uvicorn.run(app, host="0.0.0.0", port=port, debug=debug) 