"""
Redis OAuth状态管理器

主要功能：
- OAuth状态生成和验证
- 基于Redis的分布式存储
- 自动过期清理
- 与原有接口兼容
"""

import time
import secrets
import json
import httpx
import logging
from typing import Optional, Dict, Any
from config import JAVA_BACKEND_URL
from redis_client import get_redis_client

logger = logging.getLogger(__name__)

class RedisOAuthStateManager:
    """基于Redis的OAuth状态管理器"""
    
    def __init__(self):
        """初始化Redis OAuth状态管理器"""
        self.redis_client = get_redis_client()
        logger.info("Redis OAuth状态管理器初始化完成")
    
    def generate_state(self, provider: str, session_id: str = None) -> str:
        """生成并存储OAuth状态token"""
        try:
            state_token = secrets.token_urlsafe(16)
            timestamp = time.time()
            
            # 构建状态数据
            state_data = {
                'provider': provider,
                'session_id': session_id,
                'timestamp': timestamp,
                'expires_at': timestamp + 1800,  # 30分钟过期
                'created_at': time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(timestamp))
            }
            
            # 存储到Redis（10分钟过期）
            key = f"poetize:python:oauth:state:{state_token}"
            value = json.dumps(state_data, ensure_ascii=False)
            success = self.redis_client.set(key, value, ex=600)
            
            if success:
                logger.info(f"生成OAuth状态: provider={provider}, state={state_token}, session_id={session_id}")
                
                # 尝试存储到数据库（作为备份）
                try:
                    self._store_to_database(state_token, state_data)
                except Exception as e:
                    logger.warning(f"无法将状态存储到数据库: {e}")
                
                return state_token
            else:
                logger.error(f"Redis存储OAuth状态失败: provider={provider}")
                raise Exception("OAuth状态存储失败")
                
        except Exception as e:
            logger.error(f"生成OAuth状态失败: {e}")
            raise
    
    def verify_and_consume_state(self, state_token: str, provider: str) -> Optional[Dict[str, Any]]:
        """验证并消费OAuth状态token"""
        try:
            if not state_token:
                logger.warning("OAuth状态token为空")
                return None

            # 从Redis获取状态数据
            key = f"poetize:python:oauth:state:{state_token}"
            value = self.redis_client.get(key)
            state_data = json.loads(value) if value else None

            if not state_data:
                logger.warning(f"OAuth状态不存在或已过期: {state_token}")
                return None

            # 验证provider
            if state_data.get('provider') != provider:
                logger.warning(f"OAuth provider不匹配: 期望={provider}, 实际={state_data.get('provider')}")
                return None

            # 删除已使用的状态（一次性使用）
            delete_key = f"poetize:python:oauth:state:{state_token}"
            self.redis_client.delete(delete_key)

            logger.info(f"OAuth状态验证成功: provider={provider}, state={state_token}")
            return state_data

        except Exception as e:
            logger.error(f"验证OAuth状态失败: {e}")
            return None

    def validate_state(self, state_token: str, provider: str, session_id: str = None) -> bool:
        """验证OAuth状态token（向后兼容方法）"""
        try:
            state_data = self.verify_and_consume_state(state_token, provider)
            return state_data is not None
        except Exception as e:
            logger.error(f"验证OAuth状态失败: {e}")
            return False
    
    def get_state_info(self, state_token: str) -> Optional[Dict[str, Any]]:
        """获取状态信息（不消费）"""
        try:
            if not state_token:
                return None
            
            key = f"poetize:python:oauth:state:{state_token}"
            value = self.redis_client.get(key)
            state_data = json.loads(value) if value else None
            if state_data:
                logger.debug(f"获取OAuth状态信息: {state_token}")
            
            return state_data
            
        except Exception as e:
            logger.error(f"获取OAuth状态信息失败: {e}")
            return None
    
    def cleanup_expired_states(self):
        """清理过期状态（Redis自动处理，此方法保持兼容性）"""
        logger.debug("Redis自动处理过期状态，无需手动清理")
        return True
    
    def _store_to_database(self, state_token: str, state_data: Dict[str, Any]):
        """将状态存储到数据库（备份机制）"""
        try:
            # 构建存储到Java后端的请求
            store_url = f"{JAVA_BACKEND_URL}/api/oauth/state/store"
            payload = {
                'state_token': state_token,
                'provider': state_data.get('provider'),
                'session_id': state_data.get('session_id'),
                'expires_at': state_data.get('expires_at')
            }
            
            with httpx.Client(timeout=5.0) as client:
                response = client.post(store_url, json=payload)
                if response.status_code == 200:
                    logger.debug(f"OAuth状态已备份到数据库: {state_token}")
                else:
                    logger.warning(f"数据库备份失败: {response.status_code}")
                    
        except Exception as e:
            logger.warning(f"数据库备份异常: {e}")
    
    def _retrieve_from_database(self, state_token: str) -> Optional[Dict[str, Any]]:
        """从数据库检索状态（备份恢复机制）"""
        try:
            retrieve_url = f"{JAVA_BACKEND_URL}/api/oauth/state/retrieve"
            params = {'state_token': state_token}
            
            with httpx.Client(timeout=5.0) as client:
                response = client.get(retrieve_url, params=params)
                if response.status_code == 200:
                    data = response.json()
                    logger.debug(f"从数据库恢复OAuth状态: {state_token}")
                    return data
                else:
                    logger.debug(f"数据库中未找到状态: {state_token}")
                    return None
                    
        except Exception as e:
            logger.warning(f"数据库检索异常: {e}")
            return None
    
    def get_stats(self) -> Dict[str, Any]:
        """获取状态管理器统计信息"""
        try:
            # Redis健康检查
            health = {'status': 'ok', 'redis': 'connected'}
            try:
                self.redis_client.ping()
            except:
                health = {'status': 'error', 'redis': 'disconnected'}
            
            return {
                "type": "redis",
                "redis_status": health.get("status", "unknown"),
                "redis_connected": health.get("connected", False),
                "timestamp": time.strftime('%Y-%m-%d %H:%M:%S')
            }
        except Exception as e:
            logger.error(f"获取统计信息失败: {e}")
            return {
                "type": "redis",
                "redis_status": "error",
                "redis_connected": False,
                "error": str(e),
                "timestamp": time.strftime('%Y-%m-%d %H:%M:%S')
            }

# 创建全局实例
redis_oauth_state_manager = RedisOAuthStateManager()

def get_session_id(request) -> Optional[str]:
    """从请求中获取session ID（兼容性函数）"""
    try:
        # 尝试从session中获取
        if hasattr(request, 'session') and request.session:
            session_id = request.session.get('session_id')
            if session_id:
                return session_id
        
        # 尝试从cookies中获取
        if hasattr(request, 'cookies'):
            session_id = request.cookies.get('session_id')
            if session_id:
                return session_id
        
        # 尝试从headers中获取
        if hasattr(request, 'headers'):
            session_id = request.headers.get('X-Session-ID')
            if session_id:
                return session_id
        
        # 生成新的session ID
        new_session_id = secrets.token_urlsafe(16)
        logger.debug(f"生成新的session ID: {new_session_id}")
        return new_session_id
        
    except Exception as e:
        logger.error(f"获取session ID失败: {e}")
        return secrets.token_urlsafe(16)

# 为了保持向后兼容性，创建别名
oauth_state_manager = redis_oauth_state_manager

# 导出主要接口
__all__ = [
    'RedisOAuthStateManager',
    'redis_oauth_state_manager', 
    'oauth_state_manager',
    'get_session_id'
]
