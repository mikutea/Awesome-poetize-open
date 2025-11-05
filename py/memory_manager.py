"""
AI聊天记忆管理模块 - 基于 Mem0 API

主要功能：
- 使用 Mem0 云服务进行长期记忆存储
- 自动从对话中提取和存储记忆
- 智能检索相关记忆
- 用户记忆管理（增删改查）

Mem0 免费版：每月 1000 次 API 调用
官网：https://mem0.ai/
文档：https://docs.mem0.ai/
"""

import os
import json
import logging
from typing import Dict, List, Optional, Any
from datetime import datetime
import httpx

logger = logging.getLogger('memory_manager')

# Mem0 API 基础URL
MEM0_API_BASE = "https://api.mem0.ai/v1"

class MemoryManager:
    """基于 Mem0 的记忆管理器（使用 HTTP API）"""
    
    def __init__(self, api_key: str = None):
        """
        初始化记忆管理器
        
        Args:
            api_key: Mem0 API密钥，如果为None则从环境变量读取
        """
        self.api_key = api_key or os.environ.get("MEM0_API_KEY")
        self.enabled = False
        self.headers = {}
        
        if self.api_key:
            self.headers = {
                "Authorization": f"Token {self.api_key}",
                "Content-Type": "application/json"
            }
            self.enabled = True
        else:
            logger.warning("未配置 MEM0_API_KEY，记忆功能将被禁用")
            self.enabled = False
    
    def is_enabled(self) -> bool:
        """检查记忆功能是否启用"""
        return self.enabled
    
    async def add_memory_from_messages(
        self, 
        messages: List[Dict[str, str]], 
        user_id: str,
        metadata: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        从对话消息中添加记忆
        
        Args:
            messages: 对话消息列表 [{"role": "user", "content": "..."}, ...]
            user_id: 用户ID
            metadata: 额外的元数据
            
        Returns:
            添加结果
        """
        if not self.is_enabled():
            return {"success": False, "error": "记忆功能未启用"}
        
        try:
            # 准备消息格式
            formatted_messages = []
            for msg in messages:
                role = msg.get('role', 'user')
                content = msg.get('content', '')
                if content:
                    formatted_messages.append({
                        "role": role,
                        "content": content
                    })
            
            if not formatted_messages:
                return {"success": False, "error": "没有有效的消息内容"}
            
            # 调用 Mem0 API 添加记忆
            async with httpx.AsyncClient() as client:
                payload = {
                    "messages": formatted_messages,
                    "user_id": user_id
                }
                if metadata:
                    payload["metadata"] = metadata
                
                response = await client.post(
                    f"{MEM0_API_BASE}/memories/",
                    headers=self.headers,
                    json=payload,
                    timeout=30.0
                )
                response.raise_for_status()
                result = response.json()
            
            logger.info(f"为用户 {user_id} 添加记忆成功")
            return {
                "success": True,
                "result": result,
                "message": "记忆添加成功"
            }
            
        except httpx.HTTPError as e:
            logger.error(f"添加记忆HTTP错误: {e}")
            return {
                "success": False,
                "error": f"添加记忆失败: {str(e)}"
            }
        except Exception as e:
            logger.error(f"添加记忆失败: {e}")
            return {
                "success": False,
                "error": f"添加记忆失败: {str(e)}"
            }
    
    async def search_memories(
        self, 
        query: str, 
        user_id: str,
        limit: int = 5
    ) -> Dict[str, Any]:
        """
        搜索相关记忆
        
        Args:
            query: 搜索查询
            user_id: 用户ID
            limit: 返回结果数量限制
            
        Returns:
            搜索结果
        """
        if not self.is_enabled():
            return {"success": False, "error": "记忆功能未启用", "memories": []}
        
        try:
            # 调用 Mem0 API 搜索
            async with httpx.AsyncClient() as client:
                payload = {
                    "query": query,
                    "user_id": user_id,
                    "limit": limit
                }
                
                response = await client.post(
                    f"{MEM0_API_BASE}/memories/search/",
                    headers=self.headers,
                    json=payload,
                    timeout=30.0
                )
                response.raise_for_status()
                results = response.json()
            
            # 处理返回结果（可能是字典或列表）
            memories = []
            if isinstance(results, dict):
                # 如果返回的是分页格式的字典
                memories = results.get("results", [])
                total_count = results.get("count", len(memories))
            elif isinstance(results, list):
                # 如果返回的是列表
                memories = results
                total_count = len(results)
            else:
                memories = []
                total_count = 0
            
            logger.info(f"为用户 {user_id} 搜索到 {total_count} 条相关记忆")
            
            return {
                "success": True,
                "memories": memories,
                "count": total_count
            }
            
        except httpx.HTTPError as e:
            logger.error(f"搜索记忆HTTP错误: {e}")
            return {
                "success": False,
                "error": f"搜索记忆失败: {str(e)}",
                "memories": []
            }
        except Exception as e:
            logger.error(f"搜索记忆失败: {e}")
            return {
                "success": False,
                "error": f"搜索记忆失败: {str(e)}",
                "memories": []
            }
    
    async def get_all_memories(
        self, 
        user_id: str,
        page: int = 1,
        page_size: int = 50
    ) -> Dict[str, Any]:
        """
        获取用户所有记忆
        
        Args:
            user_id: 用户ID
            page: 页码
            page_size: 每页大小
            
        Returns:
            所有记忆列表
        """
        if not self.is_enabled():
            return {"success": False, "error": "记忆功能未启用", "memories": []}
        
        try:
            # 调用 Mem0 API 获取所有记忆
            async with httpx.AsyncClient() as client:
                params = {
                    "user_id": user_id,
                    "page": page,
                    "page_size": page_size
                }
                
                response = await client.get(
                    f"{MEM0_API_BASE}/memories/",
                    headers=self.headers,
                    params=params,
                    timeout=30.0
                )
                response.raise_for_status()
                results = response.json()
            
            # 处理返回结果（可能是字典或列表）
            memories = []
            if isinstance(results, dict):
                # 如果返回的是分页格式的字典
                memories = results.get("results", [])
                total_count = results.get("count", len(memories))
            elif isinstance(results, list):
                # 如果返回的是列表
                memories = results
                total_count = len(results)
            else:
                memories = []
                total_count = 0
            
            logger.info(f"获取用户 {user_id} 的记忆，第 {page} 页，共 {total_count} 条")
            
            return {
                "success": True,
                "memories": memories,
                "page": page,
                "page_size": page_size,
                "total_count": total_count
            }
            
        except httpx.HTTPError as e:
            logger.error(f"获取所有记忆HTTP错误: {e}")
            return {
                "success": False,
                "error": f"获取记忆失败: {str(e)}",
                "memories": []
            }
        except Exception as e:
            logger.error(f"获取所有记忆失败: {e}")
            return {
                "success": False,
                "error": f"获取记忆失败: {str(e)}",
                "memories": []
            }
    
    async def delete_memory(self, memory_id: str) -> Dict[str, Any]:
        """
        删除指定记忆
        
        Args:
            memory_id: 记忆ID
            
        Returns:
            删除结果
        """
        if not self.is_enabled():
            return {"success": False, "error": "记忆功能未启用"}
        
        try:
            async with httpx.AsyncClient() as client:
                response = await client.delete(
                    f"{MEM0_API_BASE}/memories/{memory_id}/",
                    headers=self.headers,
                    timeout=30.0
                )
                response.raise_for_status()
            
            logger.info(f"删除记忆 {memory_id} 成功")
            
            return {
                "success": True,
                "message": "记忆删除成功"
            }
            
        except httpx.HTTPError as e:
            logger.error(f"删除记忆HTTP错误: {e}")
            return {
                "success": False,
                "error": f"删除记忆失败: {str(e)}"
            }
        except Exception as e:
            logger.error(f"删除记忆失败: {e}")
            return {
                "success": False,
                "error": f"删除记忆失败: {str(e)}"
            }
    
    async def delete_all_memories(self, user_id: str) -> Dict[str, Any]:
        """
        删除用户所有记忆
        
        Args:
            user_id: 用户ID
            
        Returns:
            删除结果
        """
        if not self.is_enabled():
            return {"success": False, "error": "记忆功能未启用"}
        
        try:
            # 先获取所有记忆
            all_memories_result = await self.get_all_memories(user_id)
            
            if not all_memories_result.get("success"):
                return all_memories_result
            
            memories = all_memories_result.get("memories", [])
            deleted_count = 0
            
            # 逐个删除
            for memory in memories:
                # 兼容处理字符串和字典格式
                if isinstance(memory, dict):
                    memory_id = memory.get("id")
                else:
                    memory_id = None
                    
                if memory_id:
                    delete_result = await self.delete_memory(memory_id)
                    if delete_result.get("success"):
                        deleted_count += 1
            
            logger.info(f"为用户 {user_id} 删除了 {deleted_count} 条记忆")
            
            return {
                "success": True,
                "deleted_count": deleted_count,
                "message": f"已删除 {deleted_count} 条记忆"
            }
            
        except Exception as e:
            logger.error(f"删除所有记忆失败: {e}")
            return {
                "success": False,
                "error": f"删除记忆失败: {str(e)}"
            }
    
    def format_memories_for_context(self, memories) -> str:
        """
        将记忆格式化为上下文字符串，用于添加到AI提示词中
        
        Args:
            memories: 记忆列表（可以是列表、字典或其他格式）
            
        Returns:
            格式化的上下文字符串
        """
        if not memories:
            return ""
        
        # 如果 memories 是字典（分页格式），提取 results 字段
        if isinstance(memories, dict):
            memories = memories.get("results", [])
        
        # 如果 memories 不是列表，转换为列表
        if not isinstance(memories, list):
            return ""
        
        context_parts = ["**以下是关于用户的记忆信息：**\n"]
        
        for i, memory in enumerate(memories[:5], 1):  # 最多使用5条最相关的记忆
            # 兼容处理字符串和字典两种格式
            if isinstance(memory, str):
                memory_text = memory
            elif isinstance(memory, dict):
                memory_text = memory.get("memory", "") or memory.get("text", "")
            else:
                memory_text = str(memory)
            
            if memory_text:
                context_parts.append(f"{i}. {memory_text}")
        
        return "\n".join(context_parts) + "\n\n请根据这些记忆信息，提供更个性化的回复。"

# 全局记忆管理器实例
_global_memory_manager = None

def get_memory_manager(api_key: str = None) -> MemoryManager:
    """
    获取全局记忆管理器实例（单例模式）
    
    Args:
        api_key: Mem0 API密钥
        
    Returns:
        MemoryManager实例
    """
    global _global_memory_manager
    
    if _global_memory_manager is None:
        _global_memory_manager = MemoryManager(api_key=api_key)
    
    return _global_memory_manager

# 便捷函数
async def add_conversation_memory(messages: List[Dict], user_id: str) -> Dict:
    """便捷函数：添加对话记忆"""
    manager = get_memory_manager()
    return await manager.add_memory_from_messages(messages, user_id)

async def search_user_memories(query: str, user_id: str, limit: int = 5) -> List[Dict]:
    """便捷函数：搜索用户记忆"""
    manager = get_memory_manager()
    result = await manager.search_memories(query, user_id, limit)
    return result.get("memories", [])

async def get_user_all_memories(user_id: str) -> List[Dict]:
    """便捷函数：获取用户所有记忆"""
    manager = get_memory_manager()
    result = await manager.get_all_memories(user_id)
    return result.get("memories", [])
