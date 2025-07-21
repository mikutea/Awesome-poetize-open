#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
缓存管理工具
提供缓存的手动管理功能，包括清理、监控等
"""

import json
from datetime import datetime
from typing import Dict, List, Any, Optional
from cache_service import get_cache_service
from cache_constants import CacheConstants


class CacheManager:
    """缓存管理器"""
    
    def __init__(self):
        self.cache_service = get_cache_service()
    
    def clear_all_config_caches(self) -> Dict[str, Any]:
        """清理所有配置类缓存（使用刷新服务）"""
        try:
            from cache_refresh_service import get_cache_refresh_service
            refresh_service = get_cache_refresh_service()
            results = refresh_service.refresh_all_config_caches()
            return {
                "success": results.get("success", False),
                "message": "配置缓存刷新完成" if results.get("success") else "配置缓存刷新部分失败",
                "results": results,
                "timestamp": datetime.now().isoformat()
            }
        except Exception as e:
            return {
                "success": False,
                "error": f"刷新配置缓存失败: {str(e)}",
                "timestamp": datetime.now().isoformat()
            }
    
    def clear_specific_cache(self, cache_type: str) -> Dict[str, Any]:
        """清理特定类型的缓存（使用刷新服务）"""
        try:
            from cache_refresh_service import get_cache_refresh_service
            refresh_service = get_cache_refresh_service()

            # 映射缓存类型到刷新方法
            refresh_methods = {
                "seo": refresh_service.refresh_seo_caches,
                "web_info": refresh_service.refresh_web_info_caches,
                "ai_chat": refresh_service.refresh_ai_chat_caches,
                "captcha": refresh_service.refresh_captcha_caches,
                "email": refresh_service.refresh_email_caches
            }

            if cache_type not in refresh_methods:
                # 兼容旧的单个缓存键清理方式
                cache_key_map = {
                    "seo_config": CacheConstants.SEO_CONFIG_KEY,
                    "web_info_admin": CacheConstants.WEB_INFO_ADMIN_KEY,
                    "email_config": CacheConstants.EMAIL_CONFIG_KEY,
                    "captcha_config": CacheConstants.CAPTCHA_CONFIG_KEY,
                    "captcha_public_config": CacheConstants.CAPTCHA_PUBLIC_CONFIG_KEY,
                    "ai_chat_config": CacheConstants.AI_CHAT_CONFIG_KEY,
                    "seo_sitemap": CacheConstants.SEO_SITEMAP_KEY,
                    "seo_robots": CacheConstants.SEO_ROBOTS_KEY
                }

                if cache_type in cache_key_map:
                    cache_key = cache_key_map[cache_type]
                    result = self.cache_service.delete(cache_key)
                    return {
                        "success": True,
                        "message": f"缓存 {cache_type} 清理完成",
                        "deleted": result > 0,
                        "cache_key": cache_key,
                        "timestamp": datetime.now().isoformat()
                    }
                else:
                    available_types = list(refresh_methods.keys()) + list(cache_key_map.keys())
                    return {
                        "success": False,
                        "error": f"未知的缓存类型: {cache_type}",
                        "available_types": available_types
                    }

            # 使用刷新服务
            refresh_method = refresh_methods[cache_type]
            result = refresh_method()

            return {
                "success": result.get("success", False),
                "message": f"缓存类型 {cache_type} 刷新完成",
                "result": result,
                "timestamp": datetime.now().isoformat()
            }
        except Exception as e:
            return {
                "success": False,
                "error": f"刷新缓存失败: {str(e)}",
                "timestamp": datetime.now().isoformat()
            }
    
    def get_cache_overview(self) -> Dict[str, Any]:
        """获取缓存概览"""
        try:
            # 获取基本统计
            stats = self.cache_service.get_cache_stats()
            
            # 获取持久化缓存信息
            persistent_info = self.cache_service.get_persistent_cache_info()
            
            # 获取健康状态
            health = self.cache_service.health_check()
            
            return {
                "cache_stats": stats,
                "persistent_caches": persistent_info,
                "health_status": health,
                "timestamp": datetime.now().isoformat()
            }
        except Exception as e:
            return {
                "error": f"获取缓存概览失败: {str(e)}",
                "timestamp": datetime.now().isoformat()
            }
    
    def get_cache_keys_by_pattern(self, pattern: str = "poetize:*") -> Dict[str, Any]:
        """根据模式获取缓存键"""
        try:
            return self.cache_service.get_cache_keys_info(pattern)
        except Exception as e:
            return {
                "error": f"获取缓存键失败: {str(e)}",
                "keys": []
            }
    
    def analyze_cache_usage(self) -> Dict[str, Any]:
        """分析缓存使用情况"""
        try:
            # 获取所有poetize相关的键
            keys_info = self.cache_service.get_cache_keys_info("poetize:*")
            
            if "error" in keys_info:
                return keys_info
            
            # 分类统计
            categories = {
                "config_caches": [],      # 配置类缓存
                "temporary_caches": [],   # 临时缓存
                "user_caches": [],        # 用户相关缓存
                "other_caches": []        # 其他缓存
            }
            
            config_prefixes = ["seo:", "web:", "email:", "captcha:", "ai:chat:config"]
            temp_prefixes = ["ai:translate:", "ai:chat:response:", "stats:", "seo:ai:", "seo:push:"]
            user_prefixes = ["user:", "session:", "oauth:"]
            
            for key_info in keys_info.get("keys", []):
                key = key_info.get("key", "")
                key_without_prefix = key.replace("poetize:", "")
                
                categorized = False
                for prefix in config_prefixes:
                    if key_without_prefix.startswith(prefix):
                        categories["config_caches"].append(key_info)
                        categorized = True
                        break
                
                if not categorized:
                    for prefix in temp_prefixes:
                        if key_without_prefix.startswith(prefix):
                            categories["temporary_caches"].append(key_info)
                            categorized = True
                            break
                
                if not categorized:
                    for prefix in user_prefixes:
                        if key_without_prefix.startswith(prefix):
                            categories["user_caches"].append(key_info)
                            categorized = True
                            break
                
                if not categorized:
                    categories["other_caches"].append(key_info)
            
            # 统计信息
            summary = {
                "total_keys": keys_info.get("total_keys", 0),
                "config_caches_count": len(categories["config_caches"]),
                "temporary_caches_count": len(categories["temporary_caches"]),
                "user_caches_count": len(categories["user_caches"]),
                "other_caches_count": len(categories["other_caches"])
            }
            
            return {
                "summary": summary,
                "categories": categories,
                "timestamp": datetime.now().isoformat()
            }
        except Exception as e:
            return {
                "error": f"分析缓存使用情况失败: {str(e)}",
                "timestamp": datetime.now().isoformat()
            }


# 全局缓存管理器实例
_cache_manager = None

def get_cache_manager() -> CacheManager:
    """获取缓存管理器实例"""
    global _cache_manager
    if _cache_manager is None:
        _cache_manager = CacheManager()
    return _cache_manager


if __name__ == "__main__":
    # 命令行工具
    import sys
    
    manager = get_cache_manager()
    
    if len(sys.argv) < 2:
        print("用法: python cache_manager.py <command> [args]")
        print("命令:")
        print("  overview                    - 获取缓存概览")
        print("  clear-config               - 清理所有配置缓存")
        print("  clear <cache_type>         - 清理特定缓存")
        print("  refresh <config_type>      - 刷新特定配置缓存")
        print("  refresh-all                - 刷新所有配置缓存")
        print("  analyze                    - 分析缓存使用情况")
        print("  keys [pattern]             - 获取缓存键")
        print("  stats                      - 获取刷新统计")
        sys.exit(1)
    
    command = sys.argv[1]
    
    if command == "overview":
        result = manager.get_cache_overview()
        print(json.dumps(result, indent=2, ensure_ascii=False))
    
    elif command == "clear-config":
        result = manager.clear_all_config_caches()
        print(json.dumps(result, indent=2, ensure_ascii=False))
    
    elif command == "clear" and len(sys.argv) > 2:
        cache_type = sys.argv[2]
        result = manager.clear_specific_cache(cache_type)
        print(json.dumps(result, indent=2, ensure_ascii=False))

    elif command == "refresh" and len(sys.argv) > 2:
        cache_type = sys.argv[2]
        result = manager.clear_specific_cache(cache_type)
        print(json.dumps(result, indent=2, ensure_ascii=False))

    elif command == "refresh-all":
        result = manager.clear_all_config_caches()
        print(json.dumps(result, indent=2, ensure_ascii=False))

    elif command == "analyze":
        result = manager.analyze_cache_usage()
        print(json.dumps(result, indent=2, ensure_ascii=False))

    elif command == "keys":
        pattern = sys.argv[2] if len(sys.argv) > 2 else "poetize:*"
        result = manager.get_cache_keys_by_pattern(pattern)
        print(json.dumps(result, indent=2, ensure_ascii=False))

    elif command == "stats":
        from cache_refresh_service import get_cache_refresh_service
        refresh_service = get_cache_refresh_service()
        result = refresh_service.get_refresh_stats()
        print(json.dumps(result, indent=2, ensure_ascii=False))

    else:
        print(f"未知命令: {command}")
        sys.exit(1)
