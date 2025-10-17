"""
缓存辅助工具
提供Python端需要的Redis缓存功能（翻译、AI聊天、OAuth状态管理）
"""

from typing import Optional, Any
import json
from redis_client import get_redis_client

# 缓存键前缀
CACHE_PREFIX = "poetize:python:"
TRANSLATE_PREFIX = CACHE_PREFIX + "translate:"
AI_CHAT_RESPONSE_PREFIX = CACHE_PREFIX + "ai:chat:response:"
AI_CHAT_CONFIG_KEY = CACHE_PREFIX + "ai:chat:config"

# 过期时间（秒）
ONE_HOUR = 3600
ONE_DAY = 86400
ONE_WEEK = 604800


class CacheHelper:
    """缓存辅助工具"""
    
    def __init__(self):
        self.redis_client = get_redis_client()
    
    def get(self, key: str) -> Optional[Any]:
        """获取缓存"""
        try:
            value = self.redis_client.get(key)
            if value:
                return json.loads(value)
            return None
        except Exception as e:
            print(f"获取缓存失败 {key}: {e}")
            return None
    
    def set(self, key: str, value: Any, expire: int = None) -> bool:
        """设置缓存"""
        try:
            json_value = json.dumps(value, ensure_ascii=False)
            return self.redis_client.set(key, json_value, ex=expire)
        except Exception as e:
            print(f"设置缓存失败 {key}: {e}")
            return False
    
    def delete(self, key: str) -> int:
        """删除缓存"""
        try:
            return self.redis_client.delete(key)
        except Exception as e:
            print(f"删除缓存失败 {key}: {e}")
            return 0
    
    def exists(self, key: str) -> bool:
        """检查键是否存在"""
        try:
            return self.redis_client.exists(key) > 0
        except Exception as e:
            print(f"检查缓存存在失败 {key}: {e}")
            return False
    
    # ========== 翻译缓存 ==========
    def cache_translation(self, text_hash: str, translation: str, expire: int = ONE_WEEK) -> bool:
        """缓存翻译结果"""
        key = f"{TRANSLATE_PREFIX}{text_hash}"
        return self.set(key, translation, expire)
    
    def get_cached_translation(self, text_hash: str) -> Optional[str]:
        """获取缓存的翻译"""
        key = f"{TRANSLATE_PREFIX}{text_hash}"
        return self.get(key)
    
    # ========== AI聊天缓存 ==========
    def cache_ai_chat_response(self, message_hash: str, config_hash: str, response: dict, expire: int = ONE_DAY) -> bool:
        """缓存AI聊天响应"""
        key = f"{AI_CHAT_RESPONSE_PREFIX}{message_hash}:{config_hash}"
        return self.set(key, response, expire)
    
    def get_cached_ai_chat_response(self, message_hash: str, config_hash: str) -> Optional[dict]:
        """获取缓存的AI聊天响应"""
        key = f"{AI_CHAT_RESPONSE_PREFIX}{message_hash}:{config_hash}"
        return self.get(key)


# 全局实例
_cache_instance = None


def get_cache_helper() -> CacheHelper:
    """获取缓存辅助工具单例"""
    global _cache_instance
    if _cache_instance is None:
        _cache_instance = CacheHelper()
    return _cache_instance

