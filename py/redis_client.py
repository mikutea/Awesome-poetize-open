"""
Redis客户端配置模块
提供Redis连接池和统一的缓存操作接口

主要功能：
- Redis连接池管理
- 缓存键命名规范
- 统一的缓存操作方法
- 错误处理和日志记录

版本: 1.0.0
"""

import os
import json
import logging
import hashlib
from typing import Any, Optional, Union, List, Dict
from datetime import datetime, timedelta
import redis
from redis.connection import ConnectionPool
from config import REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_DB, REDIS_MAX_CONNECTIONS

# 配置日志
logger = logging.getLogger(__name__)

class RedisClient:
    """Redis客户端类"""
    
    def __init__(self):
        """初始化Redis客户端"""
        self.pool = None
        self.client = None
        self._initialize_connection()
    
    def _initialize_connection(self):
        """初始化Redis连接"""
        try:
            # 创建连接池
            self.pool = ConnectionPool(
                host=REDIS_HOST,
                port=REDIS_PORT,
                password=REDIS_PASSWORD if REDIS_PASSWORD else None,
                db=REDIS_DB,
                max_connections=REDIS_MAX_CONNECTIONS,
                decode_responses=True,  # 自动解码响应
                socket_connect_timeout=5,
                socket_timeout=5,
                retry_on_timeout=True,
                health_check_interval=30
            )
            
            # 创建Redis客户端
            self.client = redis.Redis(connection_pool=self.pool)
            
            # 测试连接
            self.client.ping()
            logger.info(f"Redis连接成功: {REDIS_HOST}:{REDIS_PORT}/{REDIS_DB}")
            
        except Exception as e:
            logger.error(f"Redis连接失败: {e}")
            # 在连接失败时使用空操作，避免程序崩溃
            self.client = None
    
    def is_connected(self) -> bool:
        """检查Redis连接状态"""
        try:
            if self.client:
                self.client.ping()
                return True
        except Exception as e:
            logger.error(f"Redis连接检查失败: {e}")
        return False
    
    def _ensure_connection(self):
        """确保Redis连接可用"""
        if not self.is_connected():
            logger.warning("Redis连接不可用，尝试重新连接...")
            self._initialize_connection()
    
    # ================================ 基础操作 ================================
    
    def set(self, key: str, value: Any, ex: Optional[int] = None) -> bool:
        """设置键值对"""
        try:
            self._ensure_connection()
            if not self.client:
                return False
            
            # 序列化复杂对象
            if isinstance(value, (dict, list)):
                value = json.dumps(value, ensure_ascii=False)
            elif not isinstance(value, (str, int, float, bool)):
                value = str(value)
            
            result = self.client.set(key, value, ex=ex)
            logger.debug(f"Redis SET: {key} = {value} (ex={ex})")
            return bool(result)
        except Exception as e:
            logger.error(f"Redis SET失败: {key} - {e}")
            return False
    
    def get(self, key: str) -> Optional[Any]:
        """获取键值"""
        try:
            self._ensure_connection()
            if not self.client:
                return None
            
            value = self.client.get(key)
            if value is None:
                return None
            
            # 尝试反序列化JSON
            try:
                return json.loads(value)
            except (json.JSONDecodeError, TypeError):
                return value
        except Exception as e:
            logger.error(f"Redis GET失败: {key} - {e}")
            return None
    
    def delete(self, *keys: str) -> int:
        """删除键"""
        try:
            self._ensure_connection()
            if not self.client:
                return 0
            
            result = self.client.delete(*keys)
            logger.debug(f"Redis DELETE: {keys}")
            return result
        except Exception as e:
            logger.error(f"Redis DELETE失败: {keys} - {e}")
            return 0
    
    def exists(self, key: str) -> bool:
        """检查键是否存在"""
        try:
            self._ensure_connection()
            if not self.client:
                return False
            
            return bool(self.client.exists(key))
        except Exception as e:
            logger.error(f"Redis EXISTS失败: {key} - {e}")
            return False
    
    def expire(self, key: str, seconds: int) -> bool:
        """设置键过期时间"""
        try:
            self._ensure_connection()
            if not self.client:
                return False
            
            result = self.client.expire(key, seconds)
            logger.debug(f"Redis EXPIRE: {key} = {seconds}s")
            return bool(result)
        except Exception as e:
            logger.error(f"Redis EXPIRE失败: {key} - {e}")
            return False
    
    def ttl(self, key: str) -> int:
        """获取键的剩余生存时间"""
        try:
            self._ensure_connection()
            if not self.client:
                return -1
            
            return self.client.ttl(key)
        except Exception as e:
            logger.error(f"Redis TTL失败: {key} - {e}")
            return -1
    
    # ================================ 计数器操作 ================================
    
    def incr(self, key: str, amount: int = 1) -> int:
        """递增计数器"""
        try:
            self._ensure_connection()
            if not self.client:
                return 0
            
            result = self.client.incr(key, amount)
            logger.debug(f"Redis INCR: {key} + {amount} = {result}")
            return result
        except Exception as e:
            logger.error(f"Redis INCR失败: {key} - {e}")
            return 0
    
    def decr(self, key: str, amount: int = 1) -> int:
        """递减计数器"""
        try:
            self._ensure_connection()
            if not self.client:
                return 0
            
            result = self.client.decr(key, amount)
            logger.debug(f"Redis DECR: {key} - {amount} = {result}")
            return result
        except Exception as e:
            logger.error(f"Redis DECR失败: {key} - {e}")
            return 0
    
    # ================================ 哈希操作 ================================
    
    def hset(self, name: str, key: str, value: Any) -> bool:
        """设置哈希字段"""
        try:
            self._ensure_connection()
            if not self.client:
                return False
            
            if isinstance(value, (dict, list)):
                value = json.dumps(value, ensure_ascii=False)
            
            result = self.client.hset(name, key, value)
            logger.debug(f"Redis HSET: {name}.{key} = {value}")
            return bool(result)
        except Exception as e:
            logger.error(f"Redis HSET失败: {name}.{key} - {e}")
            return False
    
    def hget(self, name: str, key: str) -> Optional[Any]:
        """获取哈希字段"""
        try:
            self._ensure_connection()
            if not self.client:
                return None
            
            value = self.client.hget(name, key)
            if value is None:
                return None
            
            try:
                return json.loads(value)
            except (json.JSONDecodeError, TypeError):
                return value
        except Exception as e:
            logger.error(f"Redis HGET失败: {name}.{key} - {e}")
            return None
    
    def hgetall(self, name: str) -> Dict[str, Any]:
        """获取所有哈希字段"""
        try:
            self._ensure_connection()
            if not self.client:
                return {}
            
            result = self.client.hgetall(name)
            # 尝试反序列化每个值
            for key, value in result.items():
                try:
                    result[key] = json.loads(value)
                except (json.JSONDecodeError, TypeError):
                    pass
            return result
        except Exception as e:
            logger.error(f"Redis HGETALL失败: {name} - {e}")
            return {}
    
    def hdel(self, name: str, *keys: str) -> int:
        """删除哈希字段"""
        try:
            self._ensure_connection()
            if not self.client:
                return 0
            
            result = self.client.hdel(name, *keys)
            logger.debug(f"Redis HDEL: {name}.{keys}")
            return result
        except Exception as e:
            logger.error(f"Redis HDEL失败: {name}.{keys} - {e}")
            return 0
    
    # ================================ 列表操作 ================================
    
    def lpush(self, name: str, *values: Any) -> int:
        """从左侧推入列表"""
        try:
            self._ensure_connection()
            if not self.client:
                return 0
            
            # 序列化复杂对象
            serialized_values = []
            for value in values:
                if isinstance(value, (dict, list)):
                    serialized_values.append(json.dumps(value, ensure_ascii=False))
                else:
                    serialized_values.append(str(value))
            
            result = self.client.lpush(name, *serialized_values)
            logger.debug(f"Redis LPUSH: {name} <- {values}")
            return result
        except Exception as e:
            logger.error(f"Redis LPUSH失败: {name} - {e}")
            return 0
    
    def rpop(self, name: str) -> Optional[Any]:
        """从右侧弹出列表元素"""
        try:
            self._ensure_connection()
            if not self.client:
                return None
            
            value = self.client.rpop(name)
            if value is None:
                return None
            
            try:
                return json.loads(value)
            except (json.JSONDecodeError, TypeError):
                return value
        except Exception as e:
            logger.error(f"Redis RPOP失败: {name} - {e}")
            return None
    
    def lrange(self, name: str, start: int, end: int) -> List[Any]:
        """获取列表范围"""
        try:
            self._ensure_connection()
            if not self.client:
                return []
            
            values = self.client.lrange(name, start, end)
            result = []
            for value in values:
                try:
                    result.append(json.loads(value))
                except (json.JSONDecodeError, TypeError):
                    result.append(value)
            return result
        except Exception as e:
            logger.error(f"Redis LRANGE失败: {name} - {e}")
            return []
    
    # ================================ 工具方法 ================================
    
    def generate_cache_key(self, prefix: str, *args) -> str:
        """生成缓存键"""
        parts = [prefix] + [str(arg) for arg in args if arg is not None]
        return ":".join(parts)
    
    def hash_key(self, data: str) -> str:
        """生成数据的哈希键"""
        return hashlib.md5(data.encode('utf-8')).hexdigest()
    
    def scan(self, cursor: int = 0, match: str = None, count: int = 100):
        """使用SCAN命令安全地扫描Redis键"""
        try:
            self._ensure_connection()
            if not self.client:
                return 0, []

            return self.client.scan(cursor=cursor, match=match, count=count)
        except Exception as e:
            logger.error(f"Redis SCAN操作失败: {e}")
            return 0, []

    def close(self):
        """关闭Redis连接"""
        try:
            if self.pool:
                self.pool.disconnect()
                logger.info("Redis连接池已关闭")
        except Exception as e:
            logger.error(f"关闭Redis连接失败: {e}")

# 全局Redis客户端实例
redis_client = RedisClient()

# 导出常用方法
def get_redis_client() -> RedisClient:
    """获取Redis客户端实例"""
    return redis_client
