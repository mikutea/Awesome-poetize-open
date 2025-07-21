#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
缓存自动刷新服务
提供配置更新时的自动缓存清理机制
"""

import logging
from typing import Dict, List, Any, Optional
from datetime import datetime
from cache_service import get_cache_service
from cache_constants import CacheConstants

logger = logging.getLogger(__name__)


class CacheRefreshService:
    """缓存自动刷新服务"""
    
    def __init__(self):
        self.cache_service = get_cache_service()
        self._refresh_stats = {
            "total_refreshes": 0,
            "successful_refreshes": 0,
            "failed_refreshes": 0,
            "last_refresh_time": None
        }
    
    def _log_refresh_result(self, config_type: str, cleared_keys: List[str], 
                           failed_keys: List[str] = None) -> Dict[str, Any]:
        """记录刷新结果"""
        failed_keys = failed_keys or []
        success = len(failed_keys) == 0
        
        self._refresh_stats["total_refreshes"] += 1
        if success:
            self._refresh_stats["successful_refreshes"] += 1
        else:
            self._refresh_stats["failed_refreshes"] += 1
        self._refresh_stats["last_refresh_time"] = datetime.now().isoformat()
        
        result = {
            "config_type": config_type,
            "success": success,
            "cleared_keys": cleared_keys,
            "failed_keys": failed_keys,
            "cleared_count": len(cleared_keys),
            "failed_count": len(failed_keys),
            "timestamp": datetime.now().isoformat()
        }
        
        if success:
            logger.info(f"缓存刷新成功: {config_type}, 清理了 {len(cleared_keys)} 个缓存键")
        else:
            logger.warning(f"缓存刷新部分失败: {config_type}, 成功 {len(cleared_keys)}, 失败 {len(failed_keys)}")
        
        return result
    
    def refresh_seo_caches(self) -> Dict[str, Any]:
        """刷新SEO相关缓存"""
        try:
            cleared_keys = []
            failed_keys = []
            
            # SEO相关的直接缓存键
            seo_cache_keys = [
                CacheConstants.SEO_CONFIG_KEY,      # SEO配置
                CacheConstants.SEO_SITEMAP_KEY,     # sitemap
                CacheConstants.SEO_ROBOTS_KEY       # robots.txt
            ]
            
            # 清理直接缓存键
            for cache_key in seo_cache_keys:
                try:
                    result = self.cache_service.delete(cache_key)
                    if result > 0:
                        cleared_keys.append(cache_key)
                        logger.debug(f"已清理SEO缓存: {cache_key}")
                    else:
                        logger.debug(f"SEO缓存不存在: {cache_key}")
                except Exception as e:
                    failed_keys.append(cache_key)
                    logger.error(f"清理SEO缓存失败: {cache_key} - {e}")
            
            # 清理SEO AI分析缓存（模式匹配）
            try:
                ai_analysis_pattern = f"{CacheConstants.SEO_AI_ANALYSIS_PREFIX}*"
                ai_keys = self.cache_service.redis_client.client.keys(ai_analysis_pattern)
                if ai_keys:
                    ai_cleared = self.cache_service.delete(*ai_keys)
                    cleared_keys.extend([f"SEO_AI_ANALYSIS:{i}" for i in range(ai_cleared)])
                    logger.info(f"已清理SEO AI分析缓存: {ai_cleared}个键")
            except Exception as e:
                failed_keys.append("SEO_AI_ANALYSIS_PATTERN")
                logger.error(f"清理SEO AI分析缓存失败: {e}")
            
            # 清理SEO推送状态缓存
            try:
                push_status_pattern = f"{CacheConstants.SEO_PUSH_STATUS_PREFIX}*"
                push_keys = self.cache_service.redis_client.client.keys(push_status_pattern)
                if push_keys:
                    push_cleared = self.cache_service.delete(*push_keys)
                    cleared_keys.extend([f"SEO_PUSH_STATUS:{i}" for i in range(push_cleared)])
                    logger.info(f"已清理SEO推送状态缓存: {push_cleared}个键")
            except Exception as e:
                failed_keys.append("SEO_PUSH_STATUS_PATTERN")
                logger.error(f"清理SEO推送状态缓存失败: {e}")
            
            return self._log_refresh_result("SEO", cleared_keys, failed_keys)
            
        except Exception as e:
            logger.error(f"刷新SEO缓存失败: {e}")
            return self._log_refresh_result("SEO", [], [f"GENERAL_ERROR: {str(e)}"])
    
    def refresh_web_info_caches(self) -> Dict[str, Any]:
        """刷新网站信息相关缓存"""
        try:
            cleared_keys = []
            failed_keys = []
            
            # 网站信息直接缓存键
            web_info_keys = [
                CacheConstants.WEB_INFO_KEY,
                CacheConstants.WEB_INFO_ADMIN_KEY
            ]
            
            # 清理直接缓存键
            for cache_key in web_info_keys:
                try:
                    result = self.cache_service.delete(cache_key)
                    if result > 0:
                        cleared_keys.append(cache_key)
                        logger.debug(f"已清理网站信息缓存: {cache_key}")
                except Exception as e:
                    failed_keys.append(cache_key)
                    logger.error(f"清理网站信息缓存失败: {cache_key} - {e}")
            
            # 清理网站详细信息缓存（模式匹配）
            try:
                details_pattern = f"{CacheConstants.WEB_INFO_DETAILS_PREFIX}*"
                details_keys = self.cache_service.redis_client.client.keys(details_pattern)
                if details_keys:
                    details_cleared = self.cache_service.delete(*details_keys)
                    cleared_keys.extend([f"WEB_INFO_DETAILS:{i}" for i in range(details_cleared)])
                    logger.info(f"已清理网站详细信息缓存: {details_cleared}个键")
            except Exception as e:
                failed_keys.append("WEB_INFO_DETAILS_PATTERN")
                logger.error(f"清理网站详细信息缓存失败: {e}")
            
            return self._log_refresh_result("WEB_INFO", cleared_keys, failed_keys)
            
        except Exception as e:
            logger.error(f"刷新网站信息缓存失败: {e}")
            return self._log_refresh_result("WEB_INFO", [], [f"GENERAL_ERROR: {str(e)}"])
    
    def refresh_ai_chat_caches(self) -> Dict[str, Any]:
        """刷新AI聊天相关缓存"""
        try:
            cleared_keys = []
            failed_keys = []
            
            # AI聊天配置缓存
            try:
                result = self.cache_service.delete(CacheConstants.AI_CHAT_CONFIG_KEY)
                if result > 0:
                    cleared_keys.append(CacheConstants.AI_CHAT_CONFIG_KEY)
                    logger.debug("已清理AI聊天配置缓存")
            except Exception as e:
                failed_keys.append(CacheConstants.AI_CHAT_CONFIG_KEY)
                logger.error(f"清理AI聊天配置缓存失败: {e}")
            
            # 清理AI聊天响应缓存
            try:
                response_pattern = f"{CacheConstants.AI_CHAT_RESPONSE_PREFIX}*"
                response_keys = self.cache_service.redis_client.client.keys(response_pattern)
                if response_keys:
                    response_cleared = self.cache_service.delete(*response_keys)
                    cleared_keys.extend([f"AI_CHAT_RESPONSE:{i}" for i in range(response_cleared)])
                    logger.info(f"已清理AI聊天响应缓存: {response_cleared}个键")
            except Exception as e:
                failed_keys.append("AI_CHAT_RESPONSE_PATTERN")
                logger.error(f"清理AI聊天响应缓存失败: {e}")
            
            # 清理AI聊天会话缓存
            try:
                conversation_pattern = f"{CacheConstants.AI_CHAT_CONVERSATION_PREFIX}*"
                conversation_keys = self.cache_service.redis_client.client.keys(conversation_pattern)
                if conversation_keys:
                    conversation_cleared = self.cache_service.delete(*conversation_keys)
                    cleared_keys.extend([f"AI_CHAT_CONVERSATION:{i}" for i in range(conversation_cleared)])
                    logger.info(f"已清理AI聊天会话缓存: {conversation_cleared}个键")
            except Exception as e:
                failed_keys.append("AI_CHAT_CONVERSATION_PATTERN")
                logger.error(f"清理AI聊天会话缓存失败: {e}")
            
            return self._log_refresh_result("AI_CHAT", cleared_keys, failed_keys)
            
        except Exception as e:
            logger.error(f"刷新AI聊天缓存失败: {e}")
            return self._log_refresh_result("AI_CHAT", [], [f"GENERAL_ERROR: {str(e)}"])
    
    def refresh_captcha_caches(self) -> Dict[str, Any]:
        """刷新验证码相关缓存"""
        try:
            cleared_keys = []
            failed_keys = []
            
            # 验证码配置缓存键
            captcha_keys = [
                CacheConstants.CAPTCHA_CONFIG_KEY,
                CacheConstants.CAPTCHA_PUBLIC_CONFIG_KEY
            ]
            
            # 清理验证码缓存
            for cache_key in captcha_keys:
                try:
                    result = self.cache_service.delete(cache_key)
                    if result > 0:
                        cleared_keys.append(cache_key)
                        logger.debug(f"已清理验证码缓存: {cache_key}")
                except Exception as e:
                    failed_keys.append(cache_key)
                    logger.error(f"清理验证码缓存失败: {cache_key} - {e}")
            
            return self._log_refresh_result("CAPTCHA", cleared_keys, failed_keys)
            
        except Exception as e:
            logger.error(f"刷新验证码缓存失败: {e}")
            return self._log_refresh_result("CAPTCHA", [], [f"GENERAL_ERROR: {str(e)}"])
    
    def refresh_email_caches(self) -> Dict[str, Any]:
        """刷新邮件相关缓存"""
        try:
            cleared_keys = []
            failed_keys = []
            
            # 邮件配置缓存
            try:
                result = self.cache_service.delete(CacheConstants.EMAIL_CONFIG_KEY)
                if result > 0:
                    cleared_keys.append(CacheConstants.EMAIL_CONFIG_KEY)
                    logger.debug("已清理邮件配置缓存")
            except Exception as e:
                failed_keys.append(CacheConstants.EMAIL_CONFIG_KEY)
                logger.error(f"清理邮件配置缓存失败: {e}")
            
            return self._log_refresh_result("EMAIL", cleared_keys, failed_keys)
            
        except Exception as e:
            logger.error(f"刷新邮件缓存失败: {e}")
            return self._log_refresh_result("EMAIL", [], [f"GENERAL_ERROR: {str(e)}"])
    
    def refresh_all_config_caches(self) -> Dict[str, Any]:
        """刷新所有配置相关缓存"""
        try:
            results = {
                "seo": self.refresh_seo_caches(),
                "web_info": self.refresh_web_info_caches(),
                "ai_chat": self.refresh_ai_chat_caches(),
                "captcha": self.refresh_captcha_caches(),
                "email": self.refresh_email_caches()
            }
            
            # 统计总体结果
            total_cleared = sum(len(result.get("cleared_keys", [])) for result in results.values())
            total_failed = sum(len(result.get("failed_keys", [])) for result in results.values())
            overall_success = total_failed == 0
            
            summary = {
                "success": overall_success,
                "total_cleared": total_cleared,
                "total_failed": total_failed,
                "results": results,
                "timestamp": datetime.now().isoformat()
            }
            
            logger.info(f"全量缓存刷新完成: 成功清理 {total_cleared} 个缓存, 失败 {total_failed} 个")
            return summary
            
        except Exception as e:
            logger.error(f"全量缓存刷新失败: {e}")
            return {
                "success": False,
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            }
    
    def get_refresh_stats(self) -> Dict[str, Any]:
        """获取刷新统计信息"""
        return {
            "stats": self._refresh_stats.copy(),
            "timestamp": datetime.now().isoformat()
        }


# 全局缓存刷新服务实例
_cache_refresh_service = None

def get_cache_refresh_service() -> CacheRefreshService:
    """获取缓存刷新服务实例"""
    global _cache_refresh_service
    if _cache_refresh_service is None:
        _cache_refresh_service = CacheRefreshService()
    return _cache_refresh_service
