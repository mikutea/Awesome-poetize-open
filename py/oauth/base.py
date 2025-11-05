"""
OAuth基础抽象类
定义所有OAuth提供商必须实现的接口
"""

from abc import ABC, abstractmethod
from typing import Dict, Any, Optional, Tuple
import httpx
from .exceptions import OAuthError, TokenError, UserInfoError


class BaseOAuthProvider(ABC):
    """OAuth提供商基础抽象类"""
    
    def __init__(self, config: Dict[str, Any]):
        """
        初始化OAuth提供商
        
        Args:
            config: 提供商配置字典
        """
        self.config = config
        self.provider_name = self.get_provider_name()
        
    @abstractmethod
    def get_provider_name(self) -> str:
        """获取提供商名称"""
        pass
    
    @abstractmethod
    def get_auth_url(self, state: str) -> str:
        """
        生成授权URL
        
        Args:
            state: CSRF防护状态token
            
        Returns:
            str: 授权URL
        """
        pass
    
    @abstractmethod
    async def get_access_token(self, code: str) -> str:
        """
        通过授权码获取访问令牌
        
        Args:
            code: 授权码
            
        Returns:
            str: 访问令牌
            
        Raises:
            TokenError: 获取token失败
        """
        pass
    
    @abstractmethod
    async def get_user_info(self, access_token: str) -> Dict[str, Any]:
        """
        通过访问令牌获取用户信息
        
        Args:
            access_token: 访问令牌
            
        Returns:
            Dict[str, Any]: 标准化的用户信息
            
        Raises:
            UserInfoError: 获取用户信息失败
        """
        pass
    
    def validate_config(self) -> bool:
        """
        验证配置是否完整
        
        Returns:
            bool: 配置是否有效
        """
        required_fields = self.get_required_config_fields()
        return all(self.config.get(field) for field in required_fields)
    
    @abstractmethod
    def get_required_config_fields(self) -> list:
        """获取必需的配置字段列表"""
        pass
    
    def check_email_collection_needed(self, email: str) -> Tuple[str, bool]:
        """
        检查是否需要前端收集邮箱
        
        Args:
            email: 从OAuth API获取的邮箱地址
            
        Returns:
            tuple: (processed_email, email_collection_needed)
        """
        import logging
        logger = logging.getLogger(__name__)
        
        if not email or email.strip() == "":
            return "", True
        return email.strip(), False
    
    async def handle_http_request(self, method: str, url: str, **kwargs) -> httpx.Response:
        """
        统一的HTTP请求处理
        
        Args:
            method: HTTP方法
            url: 请求URL
            **kwargs: 其他请求参数
            
        Returns:
            httpx.Response: HTTP响应
            
        Raises:
            OAuthError: 请求失败
        """
        try:
            async with httpx.AsyncClient(timeout=30) as client:
                response = await client.request(method, url, **kwargs)
                response.raise_for_status()
                return response
        except httpx.TimeoutException as e:
            raise OAuthError(f"API请求超时", "timeout", self.provider_name)
        except httpx.HTTPStatusError as e:
            raise OAuthError(f"API请求失败: {e.response.status_code}", 
                           "http_error", self.provider_name)
        except Exception as e:
            raise OAuthError(f"请求异常: {str(e)}", 
                           "request_error", self.provider_name)


class OAuth2Provider(BaseOAuthProvider):
    """OAuth 2.0提供商基础类"""
    
    def get_required_config_fields(self) -> list:
        """OAuth 2.0通用必需字段"""
        return ["client_id", "client_secret", "auth_url", "token_url", "redirect_uri"]
    
    def get_auth_url(self, state: str) -> str:
        """生成OAuth 2.0授权URL"""
        from urllib.parse import urlencode
        
        auth_params = {
            "client_id": self.config["client_id"],
            "redirect_uri": self.config["redirect_uri"],
            "scope": self.config.get("scope", ""),
            "state": state,
            "response_type": "code"
        }
        
        # 添加提供商特定参数
        additional_params = self.get_additional_auth_params()
        auth_params.update(additional_params)
        
        return f"{self.config['auth_url']}?{urlencode(auth_params)}"
    
    def get_additional_auth_params(self) -> Dict[str, str]:
        """获取提供商特定的授权参数"""
        return {}


class OAuth1Provider(BaseOAuthProvider):
    """OAuth 1.0提供商基础类（如Twitter）"""
    
    def get_required_config_fields(self) -> list:
        """OAuth 1.0通用必需字段"""
        return ["client_key", "client_secret", "request_token_url", "auth_url", "access_token_url"]
