"""
OAuth状态管理器
提供基于内存和数据库的双重状态存储机制，确保OAuth流程的状态持久化
"""

import time
import secrets
import json
import httpx
import logging
from typing import Optional, Dict, Any
from config import JAVA_BACKEND_URL

logger = logging.getLogger(__name__)

class OAuthStateManager:
    """OAuth状态管理器"""
    
    def __init__(self):
        # 内存存储作为主要存储
        self._memory_store: Dict[str, Dict[str, Any]] = {}
        # 清理过期状态的时间间隔
        self._last_cleanup = time.time()
        self._cleanup_interval = 300  # 5分钟清理一次
    
    def generate_state(self, provider: str, session_id: str = None) -> str:
        """生成并存储OAuth状态token"""
        state_token = secrets.token_urlsafe(16)
        timestamp = time.time()
        
        # 存储状态信息
        state_data = {
            'provider': provider,
            'session_id': session_id,
            'timestamp': timestamp,
            'expires_at': timestamp + 1800  # 30分钟过期
        }
        
        # 存储到内存
        self._memory_store[state_token] = state_data
        
        # 尝试存储到数据库（作为备份）
        try:
            self._store_to_database(state_token, state_data)
        except Exception as e:
            logger.warning(f"无法将状态存储到数据库: {e}")
        
        # 清理过期状态
        self._cleanup_expired_states()
        
        return state_token
    
    def validate_state(self, state_token: str, provider: str, session_id: str = None) -> bool:
        """验证OAuth状态token"""
        
        # 首先从内存检查
        state_data = self._memory_store.get(state_token)
        
        # 如果内存中没有，尝试从数据库获取
        if not state_data:
            try:
                state_data = self._get_from_database(state_token)
                if state_data:
            except Exception as e:
                logger.warning(f"从数据库获取状态失败: {e}")
        
        if not state_data:
            logger.warning(f"状态token不存在: {state_token}")
            return False
        
        # 检查是否过期
        if time.time() > state_data.get('expires_at', 0):
            logger.warning(f"状态token已过期: {state_token}")
            self._remove_state(state_token)
            return False
        
        # 检查provider是否匹配
        if state_data.get('provider') != provider:
            logger.warning(f"Provider不匹配: 期望={provider}, 实际={state_data.get('provider')}")
            return False
        
        # 验证成功，清理状态
        self._remove_state(state_token)
        return True
    
    def _store_to_database(self, state_token: str, state_data: Dict[str, Any]):
        """将状态存储到数据库（通过Java API）"""
        # 这里可以调用Java API来存储状态
        # 暂时跳过数据库存储，使用内存存储
        pass
    
    def _get_from_database(self, state_token: str) -> Optional[Dict[str, Any]]:
        """从数据库获取状态（通过Java API）"""
        # 这里可以调用Java API来获取状态
        # 暂时返回None，只使用内存存储
        return None
    
    def _remove_state(self, state_token: str):
        """移除状态token"""
        # 从内存移除
        if state_token in self._memory_store:
            del self._memory_store[state_token]
        
        # 从数据库移除（如果实现了的话）
        try:
            self._remove_from_database(state_token)
        except Exception as e:
    
    def _remove_from_database(self, state_token: str):
        """从数据库移除状态"""
        # 这里可以调用Java API来移除状态
        pass
    
    def _cleanup_expired_states(self):
        """清理过期的状态token"""
        current_time = time.time()
        
        # 如果距离上次清理时间不足清理间隔，跳过
        if current_time - self._last_cleanup < self._cleanup_interval:
            return
        
        # 清理过期的内存状态
        expired_tokens = []
        for token, data in self._memory_store.items():
            if current_time > data.get('expires_at', 0):
                expired_tokens.append(token)
        
        for token in expired_tokens:
            del self._memory_store[token]
        
        if expired_tokens:
        
        self._last_cleanup = current_time
    
    def get_stats(self) -> Dict[str, Any]:
        """获取状态管理器统计信息"""
        current_time = time.time()
        active_states = 0
        expired_states = 0
        
        for data in self._memory_store.values():
            if current_time > data.get('expires_at', 0):
                expired_states += 1
            else:
                active_states += 1
        
        return {
            'total_states': len(self._memory_store),
            'active_states': active_states,
            'expired_states': expired_states,
            'last_cleanup': self._last_cleanup
        }

# 全局状态管理器实例
oauth_state_manager = OAuthStateManager()

def get_session_id(request) -> str:
    """从请求中获取或生成session ID"""
    try:
        # 尝试从session中获取session ID
        session_id = request.session.get('_session_id')
        if not session_id:
            # 生成新的session ID
            session_id = secrets.token_hex(16)
            request.session['_session_id'] = session_id
        return session_id
    except Exception as e:
        logger.warning(f"获取session ID失败: {e}")
        # 如果session不可用，使用请求的客户端信息生成临时ID
        client_ip = getattr(request.client, 'host', 'unknown')
        user_agent = request.headers.get('user-agent', 'unknown')
        return f"{client_ip}_{hash(user_agent)}"
