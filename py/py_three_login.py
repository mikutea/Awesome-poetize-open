"""
第三方登录服务
使用工厂模式和策略模式重构的OAuth登录服务

主要改进：
1. 使用工厂模式管理OAuth提供商
2. 统一的错误处理机制
3. 可扩展的架构设计
4. 保持与现有代码的完全兼容性
"""

import os
import logging
import httpx
from typing import Dict, Any, Optional
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import RedirectResponse, JSONResponse
from starlette.middleware.sessions import SessionMiddleware

# 导入现有模块（保持兼容性）
from config import SECRET_KEY, JAVA_BACKEND_URL, FRONTEND_URL
from redis_oauth_state_manager import oauth_state_manager, get_session_id
import httpx

# 导入重构的OAuth模块
from oauth import OAuthProviderFactory, OAuthConfigManager, OAuthError, ConfigurationError
from oauth.providers.twitter import TwitterProvider

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ============================================================================
# 第三方登录配置获取函数
# ============================================================================

def get_oauth_login_config():
    """
    获取第三方登录配置（直接调用Java API）

    Returns:
        dict: 第三方登录配置字典，包含各平台配置
        None: 获取失败时返回None
    """
    try:
        java_api_url = f"{JAVA_BACKEND_URL}/webInfo/getThirdLoginConfig"
        headers = {
            "Content-Type": "application/json",
            "X-Internal-Service": "poetize-python-oauth",
            "X-Admin-Request": "true",
            "User-Agent": "poetize-python-oauth/1.0.0"
        }

        with httpx.Client(timeout=10.0) as client:
            response = client.get(java_api_url, headers=headers)
            if response.status_code == 200:
                result = response.json()
                if result.get("code") == 200 and result.get("data"):
                    logger.info("OAuth配置获取成功")
                    return result["data"]
                else:
                    logger.warning(f"Java API返回错误: {result.get('message', '未知错误')}")
                    return None
            else:
                logger.warning(f"Java API请求失败，状态码: {response.status_code}")
                return None

    except Exception as e:
        logger.error(f"获取OAuth配置失败: {str(e)}")
        return None

# 创建FastAPI应用
app = FastAPI(title="第三方登录服务", version="2.0.0")
app.add_middleware(SessionMiddleware, secret_key=SECRET_KEY)

# 初始化OAuth工厂（使用新的配置函数）
config_manager = OAuthConfigManager(config_source_func=get_oauth_login_config)
oauth_factory = OAuthProviderFactory(config_manager)


class OAuthService:
    """OAuth服务类 - 封装OAuth相关业务逻辑"""
    
    def __init__(self, factory: OAuthProviderFactory):
        self.factory = factory
    
    async def initiate_login(self, provider: str, request: Request) -> RedirectResponse:
        """
        发起OAuth登录
        
        Args:
            provider: OAuth提供商名称
            request: FastAPI请求对象
            
        Returns:
            RedirectResponse: 重定向到OAuth授权页面
        """
        try:
            # 创建提供商实例
            oauth_provider = self.factory.create_provider(provider)
            
            # 特殊处理Twitter OAuth 1.0
            if provider == "x":
                return await self._handle_twitter_login(oauth_provider, request)
            
            # 处理OAuth 2.0登录
            return await self._handle_oauth2_login(oauth_provider, request)
            
        except ConfigurationError as e:
            logger.warning(f"OAuth配置错误: {e.message}")
            return JSONResponse(
                {"error": "未配置OAuth信息，请先在后台设置"}, 
                status_code=400
            )
        except OAuthError as e:
            logger.error(f"OAuth登录失败: {e.message}")
            return JSONResponse(
                {"error": "OAuth服务暂时不可用"}, 
                status_code=500
            )
    
    async def _handle_twitter_login(self, provider: TwitterProvider, request: Request) -> RedirectResponse:
        """处理Twitter OAuth 1.0登录"""
        try:
            # 获取request token
            callback_uri = provider.config["redirect_uri"]
            request_token_data = await provider.get_request_token(callback_uri)
            
            # 存储token secret到session
            request.session["x_oauth_token_secret"] = request_token_data["oauth_token_secret"]
            
            # 生成授权URL
            auth_url = f"{provider.config['auth_url']}?oauth_token={request_token_data['oauth_token']}"
            return RedirectResponse(auth_url)
            
        except Exception as e:
            logger.error(f"Twitter登录失败: {str(e)}")
            return JSONResponse({"error": "Twitter登录服务暂时不可用"}, status_code=500)
    
    async def _handle_oauth2_login(self, provider, request: Request) -> RedirectResponse:
        """处理OAuth 2.0登录"""
        try:
            # 生成state token
            session_id = get_session_id(request)
            state = oauth_state_manager.generate_state(provider.provider_name, session_id)
            
            # 备份到session
            try:
                request.session[f"{provider.provider_name}_state"] = state
            except Exception as e:
                logger.warning(f"无法存储到session: {e}")
            
            # 生成授权URL
            auth_url = provider.get_auth_url(state)
            return RedirectResponse(auth_url)
            
        except Exception as e:
            logger.error(f"OAuth 2.0登录失败: {str(e)}")
            return JSONResponse({"error": "OAuth登录服务暂时不可用"}, status_code=500)
    
    async def handle_callback(self, provider: str, request: Request) -> RedirectResponse:
        """
        处理OAuth回调
        
        Args:
            provider: OAuth提供商名称
            request: FastAPI请求对象
            
        Returns:
            RedirectResponse: 重定向响应
        """
        try:
            # 获取回调参数
            code = request.query_params.get("code")
            state = request.query_params.get("state")
            error = request.query_params.get("error")
            
            # 检查OAuth错误
            if error:
                logger.warning(f"OAuth授权失败: provider={provider}, error={error}")
                return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error={error}&platform={provider}")
            
            # 创建提供商实例
            oauth_provider = self.factory.create_provider(provider)
            
            # 验证state（OAuth 2.0）
            if provider != "x":
                if not await self._validate_oauth2_state(state, provider):
                    return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error=state_validation_failed&platform={provider}")
            
            # 获取用户信息
            if provider == "x":
                user_data = await self._handle_twitter_callback(oauth_provider, request)
            else:
                user_data = await self._handle_oauth2_callback(oauth_provider, code)
            
            # 调用Java后端处理登录
            return await self._process_login_result(user_data, provider, request)
            
        except ConfigurationError as e:
            logger.error(f"OAuth配置错误: {e.message}")
            return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error=config_error&platform={provider}")
        except OAuthError as e:
            logger.error(f"OAuth回调处理失败: {e.message}")
            return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error=oauth_error&platform={provider}")
        except Exception as e:
            logger.error(f"OAuth回调异常: provider={provider}, error={str(e)}")
            return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error=callback_error&platform={provider}")
    
    async def _validate_oauth2_state(self, state: str, provider: str) -> bool:
        """验证OAuth 2.0 state"""
        try:
            # 使用本模块的state验证逻辑（保持兼容性）
            state_info = get_state_info_before_validation(state, provider)
            if not state_info:
                return False

            action_type = state_info.get("action", "login")
            validation_result = secure_validate_oauth_state(state, provider, action_type)

            return validation_result.get("success", False)

        except Exception as e:
            logger.error(f"State验证异常: {str(e)}")
            return False

    async def _handle_twitter_callback(self, provider: TwitterProvider, request: Request) -> Dict[str, Any]:
        """处理Twitter回调"""
        oauth_token = request.query_params.get("oauth_token")
        oauth_verifier = request.query_params.get("oauth_verifier")
        oauth_token_secret = request.session.get("x_oauth_token_secret")

        if not all([oauth_token, oauth_verifier, oauth_token_secret]):
            raise OAuthError("Twitter回调参数不完整", "invalid_params", "x")

        # 获取访问令牌
        access_token_data = await provider.get_access_token(
            oauth_token, oauth_token_secret, oauth_verifier
        )

        # 获取用户信息
        user_info = await provider.get_user_info(
            access_token_data["access_token"],
            access_token_data["access_token_secret"]
        )

        return user_info

    async def _handle_oauth2_callback(self, provider, code: str) -> Dict[str, Any]:
        """处理OAuth 2.0回调"""
        if not code:
            raise OAuthError(f"{provider.provider_name}回调缺少授权码", "missing_code", provider.provider_name)

        # 获取访问令牌
        access_token = await provider.get_access_token(code)

        # 获取用户信息
        user_info = await provider.get_user_info(access_token)

        return user_info

    async def _process_login_result(self, user_data: Dict[str, Any], provider: str, request: Request) -> RedirectResponse:
        """处理登录结果"""
        try:
            # 使用本模块的Java后端调用逻辑（保持兼容性）
            java_response = await call_java_login_api(user_data)
            response_data = java_response.json()

            if java_response.status_code == 200 and response_data.get("code") == 200:
                user_result = response_data.get("data", {})
                access_token = user_result.get("accessToken")
                response_message = response_data.get("message", "")

                if access_token:
                    # 检查是否需要邮箱收集
                    if response_message == "EMAIL_COLLECTION_NEEDED":
                        return RedirectResponse(f"{FRONTEND_URL}?userToken={access_token}&emailCollectionNeeded=true")
                    else:
                        return RedirectResponse(f"{FRONTEND_URL}?userToken={access_token}")

            # 登录失败
            error_message = response_data.get("message", "登录失败")
            return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error={error_message}&platform={provider}")

        except Exception as e:
            logger.error(f"处理登录结果失败: {str(e)}")
            return RedirectResponse(f"{FRONTEND_URL}/oauth-callback?error=login_processing_failed&platform={provider}")


# 创建OAuth服务实例
oauth_service = OAuthService(oauth_factory)


# ============================
# FastAPI路由定义
# ============================

@app.get('/login/{provider}')
async def login_route(provider: str, request: Request):
    """OAuth登录入口"""
    logger.info(f"启动 {provider} OAuth登录")
    return await oauth_service.initiate_login(provider, request)


@app.get('/callback/{provider}')
async def callback_route(provider: str, request: Request):
    """OAuth回调处理"""
    logger.info(f"处理 {provider} OAuth回调")
    return await oauth_service.handle_callback(provider, request)


@app.get('/health')
async def health_check():
    """健康检查接口"""
    return {
        "status": "ok",
        "service": "third-party-login-service-refactored",
        "version": "2.0.0",
        "supported_providers": oauth_factory.get_supported_providers(),
        "enabled_providers": oauth_factory.get_enabled_providers()
    }


@app.get('/providers')
async def get_providers():
    """获取支持的OAuth提供商信息"""
    return {
        "supported_providers": oauth_factory.get_supported_providers(),
        "enabled_providers": oauth_factory.get_enabled_providers()
    }


# ============================
# 兼容性接口
# ============================

def register_third_login_api(fastapi_app: FastAPI):
    """注册第三方登录相关API（兼容性接口）"""
    # 为了保持与原版本的完全兼容性，我们需要将路由注册到传入的app实例

    @fastapi_app.get('/login/{provider}')
    async def login_route_compat(provider: str, request: Request):
        """OAuth登录入口（兼容性路由）"""
        logger.info(f"启动 {provider} OAuth登录")
        return await oauth_service.initiate_login(provider, request)

    @fastapi_app.get('/callback/{provider}')
    async def callback_route_compat(provider: str, request: Request):
        """OAuth回调处理（兼容性路由）"""
        logger.info(f"处理 {provider} OAuth回调")
        return await oauth_service.handle_callback(provider, request)

    @fastapi_app.get('/health')
    async def health_check_compat():
        """健康检查接口（兼容性路由）"""
        return {
            "status": "ok",
            "service": "third-party-login-service"  # 保持与原版本一致
        }

    @fastapi_app.get('/oauth/providers')
    async def get_providers_compat():
        """获取支持的OAuth提供商信息（兼容性路由）"""
        return {
            "supported_providers": oauth_factory.get_supported_providers(),
            "enabled_providers": oauth_factory.get_enabled_providers()
        }

# ============================
# 原版本兼容性函数 - State验证相关
# ============================

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
                logger.warning(f"   检测到潜在的CSRF攻击：OAuth provider不匹配！")
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

# ============================
# Java后端API调用函数
# ============================

async def call_java_bind_api_direct(provider: str, code: str, state: str, state_info: dict):
    """
    直接调用Java后端的绑定接口，避免授权码过期
    优化版本：减少超时时间，快速失败
    """
    try:
        print(f"直接调用Java绑定接口: provider={provider}, code={code[:10]}..., state={state[:10]}...")

        # 从状态信息中获取用户ID用于日志记录
        user_id = state_info.get("userId") if state_info else None
        print(f"状态信息: userId={user_id}, action={state_info.get('action') if state_info else 'unknown'}")

        # 优化的请求头和超时配置
        headers = {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Internal-Service': 'poetize-python',
            'X-Admin-Request': 'true',
            'User-Agent': 'poetize-python/1.0.0'
        }

        print(f"发送绑定请求到Java后端")

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
                'X-Admin-Request': 'true',
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

# ============================
# 主要兼容性接口函数
# ============================

# 为了保持与原版本的完全兼容性，我们需要导出原有的函数名
async def oauth_login(provider: str, request: Request):
    """OAuth登录函数（兼容性接口）"""
    return await oauth_service.initiate_login(provider, request)

async def oauth_callback(provider: str, request: Request):
    """OAuth回调函数（兼容性接口）"""
    return await oauth_service.handle_callback(provider, request)


# ============================
# 扩展示例：添加新的OAuth提供商
# ============================

def add_new_provider_example():
    """
    示例：如何添加新的OAuth提供商
    这展示了重构后架构的可扩展性
    """
    from oauth.base import OAuth2Provider
    from typing import Dict, Any

    class LinkedInProvider(OAuth2Provider):
        """LinkedIn OAuth提供商示例"""

        def get_provider_name(self) -> str:
            return "linkedin"

        async def get_access_token(self, code: str) -> str:
            # LinkedIn特定的token获取逻辑
            pass

        async def get_user_info(self, access_token: str) -> Dict[str, Any]:
            # LinkedIn特定的用户信息获取逻辑
            pass

    # 注册新提供商
    linkedin_config_template = {
        "auth_url": "https://www.linkedin.com/oauth/v2/authorization",
        "token_url": "https://www.linkedin.com/oauth/v2/accessToken",
        "user_info_url": "https://api.linkedin.com/v2/people/~",
        "scope": "r_liteprofile r_emailaddress"
    }

    oauth_factory.register_provider("linkedin", LinkedInProvider, linkedin_config_template)
    print("成功添加LinkedIn OAuth提供商")


if __name__ == '__main__':
    import uvicorn

    # 第三方登录服务独立运行
    logger.info("启动独立的第三方登录OAuth服务")

    # 演示扩展性
    # add_new_provider_example()

    # 启动服务
    port = int(os.environ.get("PORT", 5001))  # 使用不同端口避免冲突
    debug = os.environ.get("ENV") == "development"
    print(f"启动第三方登录服务，端口: {port}，调试模式: {debug}")
    uvicorn.run(app, host="0.0.0.0", port=port, debug=debug, access_log=False)
