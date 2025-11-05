"""
语言工具模块
提供统一的语言映射功能，从Java后端API获取
Python后端使用中文语言映射（因为AI提示词主要是中文）
"""

import logging
from typing import Dict
from config import JAVA_BACKEND_URL
import httpx

logger = logging.getLogger(__name__)

# 默认语言映射（后端用中文，因为AI提示词主要是中文）
DEFAULT_LANGUAGE_MAP = {
    'zh': '中文',
    'zh-TW': '繁体中文',
    'en': '英文',
    'ja': '日文',
    'ko': '韩文',
    'fr': '法文',
    'de': '德文',
    'es': '西班牙文',
    'ru': '俄文',
    'pt': '葡萄牙文',
    'it': '意大利文',
    'ar': '阿拉伯文',
    'th': '泰文',
    'vi': '越南文',
    'auto': '自动检测'
}

# 缓存语言映射，避免频繁请求数据库
_cached_language_map = None


def get_language_mapping() -> Dict[str, str]:
    """
    获取语言映射配置
    优先从数据库读取，失败则使用默认配置
    
    Returns:
        语言代码到自然语言名称的映射字典
    """
    global _cached_language_map
    
    # 如果有缓存，直接返回
    if _cached_language_map is not None:
        return _cached_language_map
    
    try:
        # 从Java后端获取语言映射（后台管理用，中文）
        url = f"{JAVA_BACKEND_URL}/webInfo/ai/config/system/languageMappingAdmin"
        
        with httpx.Client(timeout=5.0) as client:
            response = client.get(url)
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200 and result.get('data'):
                    mapping = result['data']
                    _cached_language_map = mapping
                    return mapping
        
        logger.warning("语言映射获取失败，使用默认配置")
        _cached_language_map = DEFAULT_LANGUAGE_MAP
        return DEFAULT_LANGUAGE_MAP
        
    except Exception as e:
        logger.warning(f"获取语言映射配置异常，使用默认配置: {e}")
        _cached_language_map = DEFAULT_LANGUAGE_MAP
        return DEFAULT_LANGUAGE_MAP


def get_language_name(lang_code: str) -> str:
    """
    获取语言代码对应的自然语言名称
    
    Args:
        lang_code: 语言代码，如 'zh', 'en'
        
    Returns:
        自然语言名称，如 '中文', '英文'
    """
    mapping = get_language_mapping()
    return mapping.get(lang_code, lang_code)


def clear_language_mapping_cache():
    """清除语言映射缓存（当数据库配置更新时调用）"""
    global _cached_language_map
    _cached_language_map = None

