"""
AI配置客户端
从Java后端API获取AI配置供Python服务使用
"""

import logging
import os
from typing import Dict, Any, Optional
import httpx
from cache_helper import get_cache_helper

logger = logging.getLogger(__name__)

# Java后端服务地址
JAVA_BACKEND_URL = os.getenv('JAVA_BACKEND_URL', 'http://localhost:8081')


class AiConfigClient:
    """AI配置客户端（通过Java API获取）"""
    
    def __init__(self):
        """初始化配置客户端"""
        self.java_backend_url = JAVA_BACKEND_URL
        self.cache_helper = get_cache_helper()
        self.cache_ttl = 300  # 5分钟缓存
        self.client = httpx.Client(timeout=30.0)
        
    def get_ai_chat_config(self, config_name: str = 'default') -> Optional[Dict[str, Any]]:
        """
        获取AI聊天配置（从Java后端，完整未脱敏，供内部使用）
        
        Args:
            config_name: 配置名称
            
        Returns:
            配置字典（下划线格式，API密钥完整）
        """
        cache_key = f"ai_config:internal:chat:{config_name}"
        
        # 尝试从缓存获取
        cached = self.cache_helper.get(cache_key)
        if cached:
            return cached
        
        try:
            # 调用Java内部API获取完整配置（未脱敏）
            url = f"{self.java_backend_url}/webInfo/ai/config/chat/getInternal"
            headers = {'X-Internal-Service': 'poetize-python'}
            response = self.client.get(url, params={'configName': config_name}, headers=headers)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200 and result.get('data'):
                    # 转换Java驼峰格式为Python下划线格式
                    config = self._convert_to_python_format(result['data'])
                    
                    # 写入缓存
                    self.cache_helper.set(cache_key, config, self.cache_ttl)
                    
                    return config
            
            logger.warning(f"聊天配置获取失败: {config_name}")
            return None
            
        except Exception as e:
            logger.error(f"聊天配置获取异常: {e}")
            return None
    
    def get_article_ai_config(self, config_name: str = 'default') -> Optional[Dict[str, Any]]:
        """
        获取文章AI助手配置（从Java后端，完整未脱敏，供内部使用）
        包含：翻译功能、智能摘要等文章相关AI功能配置
        
        Args:
            config_name: 配置名称
            
        Returns:
            配置字典（下划线格式，API密钥完整）
        """
        cache_key = f"ai_config:internal:article_ai:{config_name}"
        
        # 尝试从缓存获取
        cached = self.cache_helper.get(cache_key)
        if cached:
            return cached
        
        try:
            # 调用Java内部API获取完整配置（未脱敏）
            url = f"{self.java_backend_url}/webInfo/ai/config/articleAi/getInternal"
            headers = {'X-Internal-Service': 'poetize-python'}
            response = self.client.get(url, params={'configName': config_name}, headers=headers)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200 and result.get('data'):
                    data = result['data']
                    
                    # 辅助函数：解析可能是JSON字符串的字段
                    def parse_json_field(field_value):
                        """解析可能是JSON字符串的字段"""
                        if field_value is None:
                            return None
                        if isinstance(field_value, str):
                            try:
                                import json
                                return json.loads(field_value)
                            except json.JSONDecodeError:
                                return None
                        return field_value
                    
                    # 转换为Python配置格式，处理JSON字符串字段
                    config = {
                        'type': data.get('translationType', 'llm'),
                        'baidu': parse_json_field(data.get('baiduConfig')),
                        'custom': parse_json_field(data.get('customConfig')),
                        'llm': parse_json_field(data.get('llmConfig')),
                        'translation_llm': parse_json_field(data.get('translationLlmConfig')),
                        'summary': parse_json_field(data.get('summaryConfig')),
                        'default_source_lang': data.get('defaultSourceLang', 'zh'),
                        'default_target_lang': data.get('defaultTargetLang', 'en'),
                    }
                    
                    # 写入缓存
                    self.cache_helper.set(cache_key, config, self.cache_ttl)
                    
                    return config
            
            logger.warning(f"文章助手配置获取失败: {config_name}")
            return None
            
        except Exception as e:
            logger.error(f"文章助手配置获取异常: {e}")
            return None
    
    def _convert_to_python_format(self, java_config: Dict[str, Any]) -> Dict[str, Any]:
        """
        将Java驼峰格式转换为Python下划线格式
        
        Args:
            java_config: Java返回的驼峰格式配置
            
        Returns:
            Python下划线格式配置
        """
        return {
            # 基础配置
            'enabled': java_config.get('enabled', False),
            'provider': java_config.get('provider', ''),
            'api_key': java_config.get('apiKey', ''),
            'api_base': java_config.get('apiBase', ''),
            'model': java_config.get('model', ''),
            
            # 聊天参数
            'temperature': java_config.get('temperature', 0.7),
            'max_tokens': java_config.get('maxTokens', 1000),
            'top_p': java_config.get('topP', 1.0),
            'frequency_penalty': java_config.get('frequencyPenalty', 0.0),
            'presence_penalty': java_config.get('presencePenalty', 0.0),
            
            # 外观设置
            'chat_name': java_config.get('chatName', 'AI助手'),
            'chat_avatar': java_config.get('chatAvatar', ''),
            'welcome_message': java_config.get('welcomeMessage', '你好！'),
            'placeholder_text': java_config.get('placeholderText', '输入...'),
            'theme_color': java_config.get('themeColor', '#4facfe'),
            
            # 功能设置
            'max_conversation_length': java_config.get('maxConversationLength', 20),
            'enable_context': java_config.get('enableContext', True),
            'enable_typing_indicator': java_config.get('enableTypingIndicator', True),
            'response_delay': java_config.get('responseDelay', 1000),
            'enable_quick_actions': java_config.get('enableQuickActions', True),
            'enable_chat_history': java_config.get('enableChatHistory', True),
            'enable_streaming': java_config.get('enableStreaming', False),
            'rate_limit': java_config.get('rateLimit', 20),
            'max_message_length': java_config.get('maxMessageLength', 500),
            'require_login': java_config.get('requireLogin', True),
            'enable_content_filter': java_config.get('enableContentFilter', True),
            
            # 高级功能
            'custom_instructions': java_config.get('customInstructions', ''),
            'enable_thinking': java_config.get('enableThinking', False),
            'enable_tools': java_config.get('enableTools', True),
            
            # 记忆功能
            'enable_memory': java_config.get('enableMemory', False),
            'mem0_api_key': java_config.get('mem0ApiKey', ''),
            'memory_auto_save': java_config.get('memoryAutoSave', True),
            'memory_auto_recall': java_config.get('memoryAutoRecall', True),
            'memory_recall_limit': java_config.get('memoryRecallLimit', 3),
        }


# 全局单例
_ai_config_client = None


def get_ai_config_client() -> AiConfigClient:
    """获取全局AI配置客户端单例"""
    global _ai_config_client
    
    if _ai_config_client is None:
        _ai_config_client = AiConfigClient()
    
    return _ai_config_client

