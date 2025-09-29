"""
OAuth提供商实现模块
包含所有支持的OAuth提供商的具体实现
"""

from .github import GitHubProvider
from .google import GoogleProvider
from .twitter import TwitterProvider
from .yandex import YandexProvider
from .gitee import GiteeProvider
from .qq import QQProvider
from .baidu import BaiduProvider

__all__ = [
    'GitHubProvider',
    'GoogleProvider', 
    'TwitterProvider',
    'YandexProvider',
    'GiteeProvider',
    'QQProvider',
    'BaiduProvider'
]
