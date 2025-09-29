"""
OAuth提供商工厂
使用工厂模式管理不同的OAuth提供商
"""

from typing import Dict, Any, Optional
from .base import BaseOAuthProvider
from .config import OAuthConfigManager
from .exceptions import ProviderNotSupportedError, ConfigurationError
from .providers import (
    GitHubProvider,
    GoogleProvider,
    TwitterProvider,
    YandexProvider,
    GiteeProvider,
    QQProvider,
    BaiduProvider
)


class OAuthProviderFactory:
    """OAuth提供商工厂类"""
    
    # 提供商映射表
    PROVIDER_CLASSES = {
        "github": GitHubProvider,
        "google": GoogleProvider,
        "x": TwitterProvider,
        "yandex": YandexProvider,
        "gitee": GiteeProvider,
        "qq": QQProvider,
        "baidu": BaiduProvider
    }
    
    def __init__(self, config_manager: OAuthConfigManager = None):
        """
        初始化工厂
        
        Args:
            config_manager: 配置管理器实例
        """
        self.config_manager = config_manager or OAuthConfigManager()
    
    def create_provider(self, provider_name: str) -> BaseOAuthProvider:
        """
        创建OAuth提供商实例
        
        Args:
            provider_name: 提供商名称
            
        Returns:
            BaseOAuthProvider: 提供商实例
            
        Raises:
            ProviderNotSupportedError: 不支持的提供商
            ConfigurationError: 配置错误
        """
        # 检查提供商是否支持
        if provider_name not in self.PROVIDER_CLASSES:
            raise ProviderNotSupportedError(
                f"不支持的OAuth提供商: {provider_name}",
                "unsupported_provider",
                provider_name
            )
        
        # 获取提供商配置
        try:
            config = self.config_manager.get_provider_config(provider_name)
        except ConfigurationError as e:
            raise e
        except Exception as e:
            raise ConfigurationError(
                f"获取{provider_name}配置失败: {str(e)}",
                "config_fetch_failed",
                provider_name
            )
        
        # 创建提供商实例
        provider_class = self.PROVIDER_CLASSES[provider_name]
        provider = provider_class(config)
        
        # 验证配置
        if not provider.validate_config():
            raise ConfigurationError(
                f"{provider_name}配置验证失败",
                "config_validation_failed",
                provider_name
            )
        
        return provider
    
    def get_supported_providers(self) -> list:
        """
        获取支持的提供商列表
        
        Returns:
            list: 支持的提供商名称列表
        """
        return list(self.PROVIDER_CLASSES.keys())
    
    def is_provider_supported(self, provider_name: str) -> bool:
        """
        检查提供商是否支持
        
        Args:
            provider_name: 提供商名称
            
        Returns:
            bool: 是否支持
        """
        return provider_name in self.PROVIDER_CLASSES
    
    def is_provider_enabled(self, provider_name: str) -> bool:
        """
        检查提供商是否启用（配置完整且可用）
        
        Args:
            provider_name: 提供商名称
            
        Returns:
            bool: 是否启用
        """
        try:
            self.create_provider(provider_name)
            return True
        except (ProviderNotSupportedError, ConfigurationError):
            return False
    
    def get_enabled_providers(self) -> list:
        """
        获取已启用的提供商列表
        
        Returns:
            list: 已启用的提供商名称列表
        """
        enabled_providers = []
        for provider_name in self.PROVIDER_CLASSES.keys():
            if self.is_provider_enabled(provider_name):
                enabled_providers.append(provider_name)
        return enabled_providers
    
    def register_provider(self, provider_name: str, provider_class: type, config_template: Dict[str, Any] = None):
        """
        注册新的OAuth提供商
        
        Args:
            provider_name: 提供商名称
            provider_class: 提供商类
            config_template: 配置模板（可选）
        """
        # 验证提供商类
        if not issubclass(provider_class, BaseOAuthProvider):
            raise ValueError(f"提供商类必须继承自BaseOAuthProvider: {provider_class}")
        
        # 注册提供商类
        self.PROVIDER_CLASSES[provider_name] = provider_class
        
        # 注册配置模板
        if config_template:
            self.config_manager.add_provider_template(provider_name, config_template)
        
        print(f"成功注册OAuth提供商: {provider_name}")
    
    def create_provider_safe(self, provider_name: str) -> Optional[BaseOAuthProvider]:
        """
        安全地创建提供商实例（不抛出异常）
        
        Args:
            provider_name: 提供商名称
            
        Returns:
            Optional[BaseOAuthProvider]: 提供商实例或None
        """
        try:
            return self.create_provider(provider_name)
        except Exception as e:
            print(f"创建{provider_name}提供商失败: {str(e)}")
            return None
