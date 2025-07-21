"""
缓存服务类 - Python版本
提供统一的缓存操作接口，与Java端保持一致

主要功能：
- 用户会话管理
- OAuth状态缓存
- 翻译结果缓存
- 访问统计缓存
- 邮件配置缓存
- 验证码缓存

版本: 1.0.0
"""

import json
import hashlib
import logging
from typing import Any, Optional, Dict, List
from datetime import datetime, timedelta
from redis_client import get_redis_client
from cache_constants import CacheConstants

logger = logging.getLogger(__name__)

class CacheService:
    """缓存服务类"""

    def __init__(self):
        """初始化缓存服务"""
        self.redis_client = get_redis_client()
        # 缓存统计
        self._cache_stats = {
            "hits": 0,
            "misses": 0,
            "sets": 0,
            "deletes": 0,
            "errors": 0
        }
    
    # ================================ 通用缓存操作 ================================
    
    def set(self, key: str, value: Any, expire_time: Optional[int] = None) -> bool:
        """设置缓存

        Args:
            key: 缓存键
            value: 缓存值
            expire_time: 过期时间（秒），None表示持久化缓存
        """
        try:
            # 如果expire_time为None，则设置为持久化缓存（不设置过期时间）
            if expire_time is None:
                result = self.redis_client.set(key, value)
            else:
                result = self.redis_client.set(key, value, ex=expire_time)

            if result:
                self._cache_stats["sets"] += 1
                if expire_time is None:
                    logger.debug(f"设置持久化缓存: {key}")
                else:
                    logger.debug(f"设置临时缓存: {key}, 过期时间: {expire_time}秒")
            else:
                self._cache_stats["errors"] += 1
            return result
        except Exception as e:
            logger.error(f"设置缓存失败: {key} - {e}")
            self._cache_stats["errors"] += 1
            return False
    
    def get(self, key: str) -> Optional[Any]:
        """获取缓存"""
        try:
            result = self.redis_client.get(key)
            if result is not None:
                self._cache_stats["hits"] += 1
            else:
                self._cache_stats["misses"] += 1
            return result
        except Exception as e:
            logger.error(f"获取缓存失败: {key} - {e}")
            self._cache_stats["errors"] += 1
            return None
    
    def delete(self, *keys: str) -> int:
        """删除缓存"""
        try:
            result = self.redis_client.delete(*keys)
            if result > 0:
                self._cache_stats["deletes"] += result
            return result
        except Exception as e:
            logger.error(f"删除缓存失败: {keys} - {e}")
            self._cache_stats["errors"] += 1
            return 0
    
    def exists(self, key: str) -> bool:
        """检查缓存是否存在"""
        try:
            return self.redis_client.exists(key)
        except Exception as e:
            logger.error(f"检查缓存存在性失败: {key} - {e}")
            return False
    
    def expire(self, key: str, seconds: int) -> bool:
        """设置缓存过期时间"""
        try:
            return self.redis_client.expire(key, seconds)
        except Exception as e:
            logger.error(f"设置缓存过期时间失败: {key} - {e}")
            return False
    
    def incr(self, key: str, amount: int = 1) -> int:
        """递增计数器"""
        try:
            return self.redis_client.incr(key, amount)
        except Exception as e:
            logger.error(f"递增计数器失败: {key} - {e}")
            return 0

    def scan_keys(self, pattern: str) -> List[str]:
        """使用SCAN命令安全地查找Redis键"""
        try:
            keys = []
            cursor = 0

            while True:
                cursor, partial_keys = self.redis_client.scan(cursor, match=pattern, count=100)
                keys.extend(partial_keys)
                if cursor == 0:
                    break

            return keys
        except Exception as e:
            logger.error(f"扫描Redis键失败: {pattern} - {e}")
            return []
    
    # ================================ OAuth状态缓存 ================================
    
    def cache_oauth_state(self, state: str, provider: str, redirect_uri: str) -> bool:
        """缓存OAuth状态"""
        try:
            key = CacheConstants.build_oauth_state_key(state)
            data = {
                "provider": provider,
                "redirect_uri": redirect_uri,
                "created_at": datetime.now().isoformat()
            }
            return self.set(key, data, CacheConstants.OAUTH_STATE_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存OAuth状态失败: {state} - {e}")
            return False
    
    def get_oauth_state(self, state: str) -> Optional[Dict[str, Any]]:
        """获取OAuth状态"""
        try:
            key = CacheConstants.build_oauth_state_key(state)
            return self.get(key)
        except Exception as e:
            logger.error(f"获取OAuth状态失败: {state} - {e}")
            return None
    
    def delete_oauth_state(self, state: str) -> bool:
        """删除OAuth状态"""
        try:
            key = CacheConstants.build_oauth_state_key(state)
            return self.delete(key) > 0
        except Exception as e:
            logger.error(f"删除OAuth状态失败: {state} - {e}")
            return False
    
    # ================================ 翻译缓存 ================================
    
    def cache_translation(self, text: str, source_lang: str, target_lang: str, 
                         translation: str, provider: str = "default") -> bool:
        """缓存翻译结果"""
        try:
            text_hash = hashlib.md5(text.encode('utf-8')).hexdigest()
            key = CacheConstants.build_ai_translate_key(text_hash, source_lang, target_lang)
            data = {
                "original_text": text,
                "translation": translation,
                "source_lang": source_lang,
                "target_lang": target_lang,
                "provider": provider,
                "cached_at": datetime.now().isoformat()
            }
            return self.set(key, data, CacheConstants.TRANSLATE_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存翻译结果失败: {text[:50]}... - {e}")
            return False
    
    def get_cached_translation(self, text: str, source_lang: str, target_lang: str) -> Optional[str]:
        """获取缓存的翻译结果"""
        try:
            text_hash = hashlib.md5(text.encode('utf-8')).hexdigest()
            key = CacheConstants.build_ai_translate_key(text_hash, source_lang, target_lang)
            data = self.get(key)
            if data and isinstance(data, dict):
                return data.get("translation")
            return None
        except Exception as e:
            logger.error(f"获取缓存翻译失败: {text[:50]}... - {e}")
            return None
    
    # ================================ 邮件配置缓存 ================================
    
    def cache_email_config(self, config: Dict[str, Any]) -> bool:
        """缓存邮件配置"""
        try:
            return self.set(CacheConstants.EMAIL_CONFIG_KEY, config, CacheConstants.CONFIG_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存邮件配置失败: {e}")
            return False
    
    def get_cached_email_config(self) -> Optional[Dict[str, Any]]:
        """获取缓存的邮件配置"""
        try:
            return self.get(CacheConstants.EMAIL_CONFIG_KEY)
        except Exception as e:
            logger.error(f"获取缓存邮件配置失败: {e}")
            return None
    
    def cache_email_test_result(self, config_hash: str, result: Dict[str, Any]) -> bool:
        """缓存邮件测试结果"""
        try:
            key = CacheConstants.build_email_test_key(config_hash)
            return self.set(key, result, CacheConstants.SHORT_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存邮件测试结果失败: {e}")
            return False
    
    def get_cached_email_test_result(self, config_hash: str) -> Optional[Dict[str, Any]]:
        """获取缓存的邮件测试结果"""
        try:
            key = CacheConstants.build_email_test_key(config_hash)
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存邮件测试结果失败: {e}")
            return None
    
    # ================================ 验证码缓存 ================================
    
    def cache_captcha(self, session_id: str, code: str) -> bool:
        """缓存验证码"""
        try:
            key = CacheConstants.build_captcha_key(session_id)
            return self.set(key, code, CacheConstants.CAPTCHA_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存验证码失败: {session_id} - {e}")
            return False
    
    def get_cached_captcha(self, session_id: str) -> Optional[str]:
        """获取缓存的验证码"""
        try:
            key = CacheConstants.build_captcha_key(session_id)
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存验证码失败: {session_id} - {e}")
            return None
    
    def delete_captcha(self, session_id: str) -> bool:
        """删除验证码缓存"""
        try:
            key = CacheConstants.build_captcha_key(session_id)
            return self.delete(key) > 0
        except Exception as e:
            logger.error(f"删除验证码缓存失败: {session_id} - {e}")
            return False
    
    # ================================ 访问统计缓存 ================================
    
    def increment_visit_count(self, date: str = None) -> int:
        """增加访问计数"""
        try:
            if not date:
                date = datetime.now().strftime("%Y-%m-%d")
            key = CacheConstants.build_visit_count_key(date)
            count = self.incr(key, 1)
            # 设置过期时间为7天
            if count == 1:
                self.expire(key, 7 * 24 * 3600)
            return count
        except Exception as e:
            logger.error(f"增加访问计数失败: {date} - {e}")
            return 0
    
    def get_visit_count(self, date: str = None) -> int:
        """获取访问计数"""
        try:
            if not date:
                date = datetime.now().strftime("%Y-%m-%d")
            key = CacheConstants.build_visit_count_key(date)
            count = self.get(key)
            return int(count) if count else 0
        except Exception as e:
            logger.error(f"获取访问计数失败: {date} - {e}")
            return 0
    
    def record_visitor_ip(self, ip: str, date: str = None) -> bool:
        """记录访问者IP"""
        try:
            if not date:
                date = datetime.now().strftime("%Y-%m-%d")
            key = CacheConstants.build_visit_ip_key(ip, date)
            result = self.set(key, datetime.now().isoformat(), 24 * 3600)  # 24小时过期
            return result
        except Exception as e:
            logger.error(f"记录访问者IP失败: {ip} - {e}")
            return False
    
    def is_ip_visited_today(self, ip: str, date: str = None) -> bool:
        """检查IP今天是否已访问"""
        try:
            if not date:
                date = datetime.now().strftime("%Y-%m-%d")
            key = CacheConstants.build_visit_ip_key(ip, date)
            return self.exists(key)
        except Exception as e:
            logger.error(f"检查IP访问状态失败: {ip} - {e}")
            return False
    
    # ================================ 系统配置缓存 ================================
    
    def cache_sys_config(self, config_key: str, config_value: Any) -> bool:
        """缓存系统配置"""
        try:
            key = CacheConstants.build_sys_config_key(config_key)
            return self.set(key, config_value, CacheConstants.CONFIG_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存系统配置失败: {config_key} - {e}")
            return False
    
    def get_cached_sys_config(self, config_key: str) -> Optional[Any]:
        """获取缓存的系统配置"""
        try:
            key = CacheConstants.build_sys_config_key(config_key)
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存系统配置失败: {config_key} - {e}")
            return None
    
    # ================================ Python API专用缓存方法 ================================

    def cache_web_info(self, web_info: Dict[str, Any], is_admin: bool = False) -> bool:
        """缓存网站信息"""
        try:
            key = CacheConstants.WEB_INFO_ADMIN_KEY if is_admin else CacheConstants.WEB_INFO_KEY
            expire_time = CacheConstants.WEB_INFO_ADMIN_EXPIRE_TIME if is_admin else CacheConstants.WEB_INFO_EXPIRE_TIME
            return self.set(key, web_info, expire_time)
        except Exception as e:
            logger.error(f"缓存网站信息失败: {e}")
            return False

    def get_cached_web_info(self, is_admin: bool = False) -> Optional[Dict[str, Any]]:
        """获取缓存的网站信息"""
        try:
            key = CacheConstants.WEB_INFO_ADMIN_KEY if is_admin else CacheConstants.WEB_INFO_KEY
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存的网站信息失败: {e}")
            return None

    def cache_web_info_details(self, auth_token_hash: str, web_info: Dict[str, Any]) -> bool:
        """缓存网站详细信息（管理员）"""
        try:
            key = CacheConstants.build_web_info_details_key(auth_token_hash)
            return self.set(key, web_info, CacheConstants.WEB_INFO_ADMIN_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存网站详细信息失败: {e}")
            return False

    def get_cached_web_info_details(self, auth_token_hash: str) -> Optional[Dict[str, Any]]:
        """获取缓存的网站详细信息"""
        try:
            key = CacheConstants.build_web_info_details_key(auth_token_hash)
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存的网站详细信息失败: {e}")
            return None

    def cache_captcha_config(self, config: Dict[str, Any], is_public: bool = False) -> bool:
        """缓存验证码配置"""
        try:
            key = CacheConstants.CAPTCHA_PUBLIC_CONFIG_KEY if is_public else CacheConstants.CAPTCHA_CONFIG_KEY
            return self.set(key, config, CacheConstants.CAPTCHA_CONFIG_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存验证码配置失败: {e}")
            return False

    def get_cached_captcha_config(self, is_public: bool = False) -> Optional[Dict[str, Any]]:
        """获取缓存的验证码配置"""
        try:
            key = CacheConstants.CAPTCHA_PUBLIC_CONFIG_KEY if is_public else CacheConstants.CAPTCHA_CONFIG_KEY
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存的验证码配置失败: {e}")
            return None

    def cache_email_config(self, config: Dict[str, Any]) -> bool:
        """缓存邮件配置"""
        try:
            return self.set(CacheConstants.EMAIL_CONFIG_KEY, config, CacheConstants.EMAIL_CONFIG_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存邮件配置失败: {e}")
            return False

    def get_cached_email_config(self) -> Optional[Dict[str, Any]]:
        """获取缓存的邮件配置"""
        try:
            return self.get(CacheConstants.EMAIL_CONFIG_KEY)
        except Exception as e:
            logger.error(f"获取缓存的邮件配置失败: {e}")
            return None

    def cache_ai_chat_config(self, config: Dict[str, Any]) -> bool:
        """缓存AI聊天配置"""
        try:
            return self.set(CacheConstants.AI_CHAT_CONFIG_KEY, config, CacheConstants.AI_CHAT_CONFIG_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存AI聊天配置失败: {e}")
            return False

    def get_cached_ai_chat_config(self) -> Optional[Dict[str, Any]]:
        """获取缓存的AI聊天配置"""
        try:
            return self.get(CacheConstants.AI_CHAT_CONFIG_KEY)
        except Exception as e:
            logger.error(f"获取缓存的AI聊天配置失败: {e}")
            return None

    def cache_ai_chat_response(self, message_hash: str, config_hash: str, response: Dict[str, Any]) -> bool:
        """缓存AI聊天响应"""
        try:
            key = CacheConstants.build_ai_chat_response_key(message_hash, config_hash)
            return self.set(key, response, CacheConstants.AI_CHAT_RESPONSE_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存AI聊天响应失败: {e}")
            return False

    def get_cached_ai_chat_response(self, message_hash: str, config_hash: str) -> Optional[Dict[str, Any]]:
        """获取缓存的AI聊天响应"""
        try:
            key = CacheConstants.build_ai_chat_response_key(message_hash, config_hash)
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存的AI聊天响应失败: {e}")
            return None

    def cache_seo_ai_analysis(self, site_info_hash: str, analysis: Dict[str, Any]) -> bool:
        """缓存SEO AI分析结果"""
        try:
            key = CacheConstants.build_seo_ai_analysis_key(site_info_hash)
            return self.set(key, analysis, CacheConstants.SEO_AI_ANALYSIS_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存SEO AI分析失败: {e}")
            return False

    def get_cached_seo_ai_analysis(self, site_info_hash: str) -> Optional[Dict[str, Any]]:
        """获取缓存的SEO AI分析结果"""
        try:
            key = CacheConstants.build_seo_ai_analysis_key(site_info_hash)
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存的SEO AI分析失败: {e}")
            return None

    def cache_seo_push_status(self, url_hash: str, engine: str, status: Dict[str, Any]) -> bool:
        """缓存SEO推送状态"""
        try:
            key = CacheConstants.build_seo_push_status_key(url_hash, engine)
            return self.set(key, status, CacheConstants.SEO_PUSH_STATUS_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存SEO推送状态失败: {e}")
            return False

    def get_cached_seo_push_status(self, url_hash: str, engine: str) -> Optional[Dict[str, Any]]:
        """获取缓存的SEO推送状态"""
        try:
            key = CacheConstants.build_seo_push_status_key(url_hash, engine)
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存的SEO推送状态失败: {e}")
            return None

    def cache_visit_stats(self, days: int, stats: List[Dict[str, Any]]) -> bool:
        """缓存访问统计数据"""
        try:
            key = CacheConstants.build_visit_stats_daily_key(days)
            return self.set(key, stats, CacheConstants.VISIT_STATS_EXPIRE_TIME)
        except Exception as e:
            logger.error(f"缓存访问统计失败: {e}")
            return False

    def get_cached_visit_stats(self, days: int) -> Optional[List[Dict[str, Any]]]:
        """获取缓存的访问统计数据"""
        try:
            key = CacheConstants.build_visit_stats_daily_key(days)
            return self.get(key)
        except Exception as e:
            logger.error(f"获取缓存的访问统计失败: {e}")
            return None

    # ================================ 缓存防护机制 ================================

    def set_with_null_protection(self, key: str, value: Any, expire_time: Optional[int] = None,
                                 null_expire_time: int = 60) -> bool:
        """设置缓存，支持空值保护（防缓存穿透）"""
        try:
            if value is None:
                # 缓存空值，但使用较短的过期时间
                return self.set(key, "__NULL__", null_expire_time)
            else:
                return self.set(key, value, expire_time)
        except Exception as e:
            logger.error(f"设置缓存（空值保护）失败: {key} - {e}")
            return False

    def get_with_null_protection(self, key: str) -> Optional[Any]:
        """获取缓存，支持空值保护"""
        try:
            value = self.get(key)
            if value == "__NULL__":
                return None
            return value
        except Exception as e:
            logger.error(f"获取缓存（空值保护）失败: {key} - {e}")
            return None

    def set_with_random_expire(self, key: str, value: Any, base_expire_time: Optional[int],
                              random_range: int = 60) -> bool:
        """设置缓存，使用随机过期时间（防缓存雪崩）

        Args:
            key: 缓存键
            value: 缓存值
            base_expire_time: 基础过期时间，None表示持久化缓存
            random_range: 随机时间范围
        """
        try:
            # 如果base_expire_time为None，则设置为持久化缓存
            if base_expire_time is None:
                return self.set(key, value, None)

            import random
            actual_expire_time = base_expire_time + random.randint(0, random_range)
            return self.set(key, value, actual_expire_time)
        except Exception as e:
            logger.error(f"设置缓存（随机过期）失败: {key} - {e}")
            return False

    def get_or_set(self, key: str, data_loader, expire_time: Optional[int] = None,
                   use_null_protection: bool = True, use_random_expire: bool = True) -> Optional[Any]:
        """获取缓存，如果不存在则通过data_loader加载并缓存"""
        try:
            # 先尝试从缓存获取
            if use_null_protection:
                cached_value = self.get_with_null_protection(key)
            else:
                cached_value = self.get(key)

            if cached_value is not None:
                return cached_value

            # 缓存不存在，通过data_loader加载数据
            try:
                data = data_loader()
            except Exception as e:
                logger.error(f"数据加载器执行失败: {key} - {e}")
                return None

            # 缓存数据
            if use_null_protection:
                self.set_with_null_protection(key, data, expire_time)
            elif use_random_expire and expire_time is not None:
                # 只有当expire_time不为None时才使用随机过期
                self.set_with_random_expire(key, data, expire_time)
            else:
                # 直接设置缓存，支持持久化缓存（expire_time为None）
                self.set(key, data, expire_time)

            return data
        except Exception as e:
            logger.error(f"获取或设置缓存失败: {key} - {e}")
            return None

    # ================================ 持久化缓存管理 ================================

    def set_persistent_cache(self, key: str, value: Any) -> bool:
        """设置持久化缓存（永不过期）"""
        return self.set(key, value, None)

    def clear_config_caches(self) -> Dict[str, bool]:
        """清理所有配置类持久化缓存"""
        try:
            results = {}
            config_keys = [
                CacheConstants.EMAIL_CONFIG_KEY,
                CacheConstants.CAPTCHA_CONFIG_KEY,
                CacheConstants.CAPTCHA_PUBLIC_CONFIG_KEY,
                CacheConstants.SEO_CONFIG_KEY,
                CacheConstants.AI_CHAT_CONFIG_KEY,
                CacheConstants.WEB_INFO_KEY,
                CacheConstants.WEB_INFO_ADMIN_KEY
            ]

            for key in config_keys:
                try:
                    result = self.delete(key)
                    results[key] = result > 0
                    logger.info(f"清理配置缓存: {key} - {'成功' if result > 0 else '未找到'}")
                except Exception as e:
                    results[key] = False
                    logger.error(f"清理配置缓存失败: {key} - {e}")

            # 清理网站详细信息缓存（模式匹配）
            try:
                pattern_key = f"{CacheConstants.WEB_INFO_DETAILS_PREFIX}*"
                keys = self.redis_client.client.keys(pattern_key)
                if keys:
                    deleted_count = self.delete(*keys)
                    results[pattern_key] = deleted_count > 0
                    logger.info(f"清理网站详细信息缓存: {deleted_count}个键")
                else:
                    results[pattern_key] = True
            except Exception as e:
                results[pattern_key] = False
                logger.error(f"清理网站详细信息缓存失败: {e}")

            return results
        except Exception as e:
            logger.error(f"批量清理配置缓存失败: {e}")
            return {}

    def get_persistent_cache_info(self) -> Dict[str, Any]:
        """获取持久化缓存信息"""
        try:
            config_keys = [
                CacheConstants.EMAIL_CONFIG_KEY,
                CacheConstants.CAPTCHA_CONFIG_KEY,
                CacheConstants.CAPTCHA_PUBLIC_CONFIG_KEY,
                CacheConstants.SEO_CONFIG_KEY,
                CacheConstants.AI_CHAT_CONFIG_KEY,
                CacheConstants.WEB_INFO_KEY,
                CacheConstants.WEB_INFO_ADMIN_KEY
            ]

            cache_info = {}
            for key in config_keys:
                try:
                    exists = self.redis_client.client.exists(key)
                    ttl = self.redis_client.client.ttl(key) if exists else None
                    cache_info[key] = {
                        "exists": bool(exists),
                        "ttl": ttl,
                        "is_persistent": ttl == -1  # -1表示永不过期
                    }
                except Exception as e:
                    cache_info[key] = {"error": str(e)}

            return {
                "persistent_caches": cache_info,
                "timestamp": datetime.now().isoformat()
            }
        except Exception as e:
            logger.error(f"获取持久化缓存信息失败: {e}")
            return {"error": str(e)}

    # ================================ 缓存统计和监控 ================================

    def get_cache_stats(self) -> Dict[str, Any]:
        """获取缓存统计信息"""
        try:
            total_operations = self._cache_stats["hits"] + self._cache_stats["misses"]
            hit_rate = (self._cache_stats["hits"] / total_operations * 100) if total_operations > 0 else 0

            # 获取Redis内存信息
            redis_info = {}
            try:
                if self.redis_client.is_connected():
                    info = self.redis_client.client.info('memory')
                    redis_info = {
                        "used_memory": info.get('used_memory', 0),
                        "used_memory_human": info.get('used_memory_human', '0B'),
                        "used_memory_peak": info.get('used_memory_peak', 0),
                        "used_memory_peak_human": info.get('used_memory_peak_human', '0B'),
                        "maxmemory": info.get('maxmemory', 0),
                        "maxmemory_human": info.get('maxmemory_human', '0B') if info.get('maxmemory', 0) > 0 else 'unlimited'
                    }
            except Exception as e:
                logger.warning(f"获取Redis内存信息失败: {e}")

            return {
                "cache_stats": {
                    "hits": self._cache_stats["hits"],
                    "misses": self._cache_stats["misses"],
                    "sets": self._cache_stats["sets"],
                    "deletes": self._cache_stats["deletes"],
                    "errors": self._cache_stats["errors"],
                    "hit_rate": round(hit_rate, 2),
                    "total_operations": total_operations
                },
                "redis_info": redis_info,
                "timestamp": datetime.now().isoformat()
            }
        except Exception as e:
            logger.error(f"获取缓存统计失败: {e}")
            return {
                "cache_stats": self._cache_stats.copy(),
                "redis_info": {},
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            }

    def reset_cache_stats(self) -> bool:
        """重置缓存统计"""
        try:
            self._cache_stats = {
                "hits": 0,
                "misses": 0,
                "sets": 0,
                "deletes": 0,
                "errors": 0
            }
            return True
        except Exception as e:
            logger.error(f"重置缓存统计失败: {e}")
            return False

    def get_cache_keys_info(self, pattern: str = "poetize:*") -> Dict[str, Any]:
        """获取缓存键信息"""
        try:
            if not self.redis_client.is_connected():
                return {"error": "Redis未连接", "keys": []}

            keys = self.redis_client.client.keys(pattern)
            key_info = []

            for key in keys[:100]:  # 限制返回数量，避免性能问题
                try:
                    ttl = self.redis_client.client.ttl(key)
                    key_type = self.redis_client.client.type(key)
                    memory_usage = self.redis_client.client.memory_usage(key) if hasattr(self.redis_client.client, 'memory_usage') else 0

                    key_info.append({
                        "key": key,
                        "type": key_type,
                        "ttl": ttl,
                        "memory_usage": memory_usage
                    })
                except Exception as e:
                    logger.warning(f"获取键信息失败: {key} - {e}")

            return {
                "total_keys": len(keys),
                "displayed_keys": len(key_info),
                "keys": key_info,
                "timestamp": datetime.now().isoformat()
            }
        except Exception as e:
            logger.error(f"获取缓存键信息失败: {e}")
            return {"error": str(e), "keys": []}

    # ================================ 健康检查 ================================
    
    def health_check(self) -> Dict[str, Any]:
        """Redis健康检查"""
        try:
            if self.redis_client.is_connected():
                # 测试基本操作
                test_key = "poetize:health:test"
                test_value = datetime.now().isoformat()
                
                # 测试写入
                write_success = self.set(test_key, test_value, 60)
                
                # 测试读取
                read_value = self.get(test_key)
                read_success = read_value == test_value
                
                # 清理测试数据
                self.delete(test_key)
                
                return {
                    "status": "healthy" if (write_success and read_success) else "degraded",
                    "connected": True,
                    "write_test": write_success,
                    "read_test": read_success,
                    "timestamp": datetime.now().isoformat()
                }
            else:
                return {
                    "status": "unhealthy",
                    "connected": False,
                    "error": "Redis连接失败",
                    "timestamp": datetime.now().isoformat()
                }
        except Exception as e:
            return {
                "status": "unhealthy",
                "connected": False,
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            }

# 全局缓存服务实例
cache_service = CacheService()

def get_cache_service() -> CacheService:
    """获取缓存服务实例"""
    return cache_service
