"""
JSON配置文件统一缓存管理器
提供纯永久缓存模式，实现零文件系统访问的高效读取
"""

import os
import json
import logging
from typing import Dict, Any, Optional, List, Tuple
from redis_client import get_redis_client

logger = logging.getLogger(__name__)


class JsonConfigCache:
    """JSON配置文件缓存管理器

    缓存策略：纯永久缓存模式
    - 配置数据设置为永久缓存，不设置过期时间
    - 移除文件哈希检测机制，实现零文件系统访问的读取
    - 完全依赖应用层主动缓存管理，配置保存时手动刷新
    - 适用于配置文件修改频率极低的场景

    特性：
    1. 纯缓存读取，最大化读取性能
    2. 零文件系统访问，消除哈希计算开销
    3. 统一的缓存接口和管理
    4. 主动缓存刷新机制
    5. 详细的性能监控和统计
    """
    
    def __init__(self):
        self.redis_client = get_redis_client()
        self.cache_prefix = "poetize:python:json_config:"
        
        # JSON配置文件映射
        self.config_files = {
            'ai_chat_config': 'data/ai_chat_config.json',
            'ai_api_config': 'data/ai_api_config.json',
            'translation_config': 'data/translation_config.json'
        }
    
    def _get_cache_key(self, config_name: str) -> str:
        """获取配置的缓存键"""
        return f"{self.cache_prefix}{config_name}"
    

    
    def get_json_config(self, config_name: str, file_path: str = None) -> Optional[Dict[str, Any]]:
        """获取JSON配置（永久缓存）

        缓存策略：
        - 优先从永久缓存获取配置数据
        - 缓存不存在时读取文件并建立永久缓存
        - 零文件系统访问，无哈希计算开销
        - 完全依赖应用层主动缓存管理

        Args:
            config_name: 配置名称
            file_path: 文件路径（可选，如果不提供则从预定义映射中获取）

        Returns:
            配置字典或None
        """
        try:
            # 获取缓存键
            cache_key = self._get_cache_key(config_name)

            # 优先从缓存获取
            cached_value = self.redis_client.get(cache_key)
            if cached_value:
                # 处理可能的数据类型（字符串或已反序列化的字典）
                if isinstance(cached_value, dict):
                    cached_config = cached_value
                elif isinstance(cached_value, (str, bytes)):
                    try:
                        cached_config = json.loads(cached_value)
                    except:
                        # 缓存损坏，删除并重新加载
                        self.redis_client.delete(cache_key)
                        cached_config = None
                else:
                    cached_config = None
                
                if cached_config:
                    logger.debug(f"从缓存获取JSON配置: {config_name}")
                    return cached_config

            # 缓存不存在，从文件读取

            # 确定文件路径
            if not file_path:
                file_path = self.config_files.get(config_name)
                if not file_path:
                    logger.error(f"未知的配置名称: {config_name}")
                    return None

            # 检查文件是否存在
            if not os.path.exists(file_path):
                logger.warning(f"配置文件不存在: {file_path}")
                return None

            # 读取文件并建立永久缓存
            with open(file_path, 'r', encoding='utf-8') as f:
                config = json.load(f)

            # 建立永久缓存
            self.redis_client.set(cache_key, json.dumps(config, ensure_ascii=False))

            logger.debug(f"读取文件并建立缓存: {config_name}")
            return config

        except Exception as e:
            logger.error(f"获取JSON配置失败: {config_name} - {e}")
            return None
    
    def invalidate_json_cache(self, config_name: str) -> bool:
        """手动失效并立即刷新指定配置的永久缓存

        用于配置保存后主动刷新缓存，确保立即可用最新数据。
        采用"删除+立即重建"策略，适用于配置保存后立即使用的场景。

        Args:
            config_name: 配置名称

        Returns:
            是否成功刷新
        """
        try:
            cache_key = self._get_cache_key(config_name)

            # 删除旧缓存
            self.redis_client.delete(cache_key)

            # 立即重建缓存
            new_config = self.get_json_config(config_name)
            if new_config is not None:
                logger.info(f"已刷新JSON配置永久缓存: {config_name}")
                return True
            else:
                logger.warning(f"刷新JSON配置缓存失败，无法读取配置文件: {config_name}")
                return False

        except Exception as e:
            logger.error(f"刷新JSON配置缓存失败: {config_name} - {e}")
            return False
    
    def refresh_all_json_caches(self) -> Dict[str, bool]:
        """刷新所有JSON配置的永久缓存

        清除所有配置的永久缓存并重新加载，用于：
        - 手动刷新所有配置
        - Redis重启后的缓存重建
        - 批量配置更新后的缓存同步

        Returns:
            每个配置的刷新结果
        """
        results = {}
        for config_name in self.config_files.keys():
            try:
                # 先失效缓存
                self.invalidate_json_cache(config_name)
                # 重新加载
                config = self.get_json_config(config_name)
                results[config_name] = config is not None
            except Exception as e:
                logger.error(f"刷新配置缓存失败: {config_name} - {e}")
                results[config_name] = False
        
        logger.info(f"批量刷新JSON配置缓存完成: {results}")
        return results
    
    def warmup_all_caches(self) -> Dict[str, Any]:
        """预热所有JSON配置缓存
        
        在应用启动时预加载所有配置文件到永久缓存，确保首次访问时无需从文件系统读取
        
        Returns:
            预热结果统计信息
        """
        start_time = __import__('time').time()
        logger.info("开始预热JSON配置缓存...")
        
        success_count = 0
        failed_count = 0
        skipped_count = 0
        results = {}
        
        for config_name, file_path in self.config_files.items():
            try:
                # 检查文件是否存在
                if not os.path.exists(file_path):
                    logger.warning(f"配置文件不存在，跳过预热: {file_path}")
                    results[config_name] = {
                        'status': 'skipped',
                        'reason': 'file_not_found'
                    }
                    skipped_count += 1
                    continue
                
                # 删除现有缓存
                cache_key = self._get_cache_key(config_name)
                self.redis_client.delete(cache_key)
                
                # 执行预热
                logger.info(f"预热配置: {config_name} (路径: {file_path})")
                config = self.get_json_config(config_name)
                
                if config is not None:
                    results[config_name] = {
                        'status': 'success',
                        'size': len(str(config))
                    }
                    success_count += 1
                    logger.info(f"成功预热配置: {config_name}")
                else:
                    results[config_name] = {
                        'status': 'failed',
                        'reason': 'load_error'
                    }
                    failed_count += 1
                    logger.error(f"预热配置失败: {config_name}")
            
            except Exception as e:
                results[config_name] = {
                    'status': 'error',
                    'reason': str(e)
                }
                failed_count += 1
                logger.error(f"预热配置异常: {config_name} - {e}")
        
        execution_time = __import__('time').time() - start_time
        
        summary = {
            'total_configs': len(self.config_files),
            'success_count': success_count,
            'failed_count': failed_count,
            'skipped_count': skipped_count,
            'execution_time_ms': int(execution_time * 1000),
            'results': results
        }
        
        logger.info(f"JSON配置缓存预热完成: {success_count}成功, {failed_count}失败, {skipped_count}跳过, 耗时{execution_time:.2f}秒")
        return summary
    
    def get_cache_stats(self) -> Dict[str, Any]:
        """获取缓存统计信息（纯永久缓存模式）"""
        total_requests = self._stats['cache_hits'] + self._stats['cache_misses']
        hit_rate = (self._stats['cache_hits'] / total_requests * 100) if total_requests > 0 else 0

        return {
            'cache_hits': self._stats['cache_hits'],
            'cache_misses': self._stats['cache_misses'],
            'file_reads': self._stats['file_reads'],
            'hit_rate_percent': round(hit_rate, 2),
            'total_requests': total_requests,
            'cache_mode': 'pure_permanent'  # 标识缓存模式
        }
    
    def reset_stats(self):
        """重置统计信息"""
        self._stats = {
            'cache_hits': 0,
            'cache_misses': 0,
            'file_reads': 0
        }
        logger.info("JSON配置缓存统计信息已重置（纯永久缓存模式）")


# 全局实例
_json_config_cache = None

def get_json_config_cache() -> JsonConfigCache:
    """获取JSON配置缓存管理器实例（单例模式）"""
    global _json_config_cache
    if _json_config_cache is None:
        _json_config_cache = JsonConfigCache()
    return _json_config_cache
