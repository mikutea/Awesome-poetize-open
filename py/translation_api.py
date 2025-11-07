"""
翻译管理API服务
提供翻译配置管理、翻译服务接口
"""

import json
import httpx
from typing import Dict, Any, Optional
from fastapi import Depends
from pydantic import BaseModel, Field
from auth_decorator import admin_required
import logging
import time
import hashlib
# 导入OpenAI官方库
import openai
from config import JAVA_BACKEND_URL
from cache_helper import get_cache_helper
# 导入TOON格式库（用于优化LLM翻译的token消耗）
from toon import encode as toon_encode, decode as toon_decode
# 导入tiktoken库（用于准确计算token消耗）
import tiktoken
logger = logging.getLogger(__name__)

class TranslationConfig(BaseModel):
    """翻译配置模型"""
    type: str = Field(..., description="翻译类型: none|baidu|custom|llm|dedicated_llm")
    
    # 百度翻译配置
    baidu: Optional[dict] = Field(None, description="百度翻译配置")
    
    # 自定义API配置  
    custom: Optional[dict] = Field(None, description="自定义API配置")
    
    # 大模型配置
    llm: Optional[dict] = Field(None, description="大模型配置")
    
    # 翻译独立大模型配置
    translation_llm: Optional[dict] = Field(None, description="翻译独立大模型配置")
    
    # 摘要生成配置
    summary: Optional[dict] = Field(None, description="摘要生成配置")
    
    # 默认语言配置
    default_source_lang: Optional[str] = Field("zh", description="默认源语言")
    default_target_lang: Optional[str] = Field("en", description="默认目标语言")

class TranslationRequest(BaseModel):
    """翻译请求模型"""
    text: Optional[str] = Field(None, description="待翻译文本（单独翻译时使用）")
    title: Optional[str] = Field(None, description="文章标题（与content一起使用TOON格式）")
    content: Optional[str] = Field(None, description="文章内容（与title一起使用TOON格式）")
    source_lang: Optional[str] = Field("auto", description="源语言代码")
    target_lang: Optional[str] = Field("en", description="目标语言代码")

class TestTranslationRequest(BaseModel):
    """测试翻译请求模型（支持TOON格式测试）"""
    text: Optional[str] = Field(None, description="待翻译文本（单文本测试）")
    title: Optional[str] = Field(None, description="文章标题（TOON格式测试）")
    content: Optional[str] = Field(None, description="文章内容（TOON格式测试）")
    config: Optional[dict] = Field(None, description="可选的临时配置，用于测试未保存的配置")

class TranslationResponse(BaseModel):
    """翻译响应模型（支持TOON格式优化）"""
    success: bool = Field(..., description="翻译是否成功")
    translated_text: Optional[str] = Field(None, description="翻译结果（单文本翻译）")
    translated_title: Optional[str] = Field(None, description="翻译后的标题（TOON格式）")
    translated_content: Optional[str] = Field(None, description="翻译后的内容（TOON格式）")
    source_lang: Optional[str] = Field(None, description="检测到的源语言")
    target_lang: Optional[str] = Field(None, description="目标语言")
    engine: Optional[str] = Field(None, description="使用的翻译引擎")
    toon_tokens: Optional[int] = Field(None, description="TOON格式消耗的token数")
    token_saved_percent: Optional[float] = Field(None, description="使用TOON节省的token百分比")
    error_message: Optional[str] = Field(None, description="错误信息")

class SummaryRequest(BaseModel):
    """摘要生成请求模型"""
    article_id: int = Field(..., description="文章ID")
    content: Optional[str] = Field(None, description="待生成摘要的文章内容（源语言）")
    languages: Optional[Dict[str, str]] = Field(None, description="各语言的文章内容，格式：{\"zh\": \"中文内容\", \"en\": \"English content\"}")
    max_length: Optional[int] = Field(150, description="摘要最大长度")
    style: Optional[str] = Field("concise", description="摘要风格：concise简洁|detailed详细|academic学术")
    config: Optional[dict] = Field(None, description="可选的临时配置，用于测试未保存的配置")

class SummaryResponse(BaseModel):
    """摘要生成响应模型"""
    success: bool = Field(..., description="摘要生成是否成功")
    summaries: Optional[Dict[str, str]] = Field(None, description="生成的多语言摘要JSON，格式：{\"zh\": \"中文摘要\", \"en\": \"English summary\"}")
    method: Optional[str] = Field(None, description="使用的摘要方法：ai|textrank")
    processing_time: Optional[float] = Field(None, description="处理时间（秒）")
    toon_tokens: Optional[int] = Field(None, description="TOON格式消耗的token数")
    token_saved_percent: Optional[float] = Field(None, description="使用TOON格式节省的token百分比")
    error_message: Optional[str] = Field(None, description="错误信息")

class TranslationManager:
    """翻译管理器（配置从Java后端获取）"""

    def __init__(self):
        # 集成Redis缓存服务
        self.cache_service = get_cache_helper()
        # 初始化tokenizer缓存
        self._tokenizer_cache = {}

    def _get_tokenizer(self, model: str = None):
        """获取tokenizer（根据模型选择，带缓存）
        
        Args:
            model: 模型名称，用于选择合适的encoding
            
        Returns:
            tiktoken.Encoding对象
        """
        # 根据模型选择encoding
        encoding_name = "cl100k_base"  # 默认用GPT-4/GPT-3.5的encoding
        
        if model:
            model_lower = model.lower()
            if any(x in model_lower for x in ['gpt-4', 'gpt-3.5', 'gpt-35', 'text-embedding']):
                encoding_name = "cl100k_base"
            elif any(x in model_lower for x in ['davinci', 'curie', 'babbage', 'ada']):
                encoding_name = "p50k_base"
            elif 'claude' in model_lower:
                # Claude使用类似GPT-4的encoding作为近似
                encoding_name = "cl100k_base"
            elif any(x in model_lower for x in ['deepseek', 'qwen', 'glm', 'llama']):
                # 国产模型大多兼容GPT-4的tokenizer
                encoding_name = "cl100k_base"
        
        # 缓存tokenizer，避免重复加载
        if encoding_name not in self._tokenizer_cache:
            try:
                self._tokenizer_cache[encoding_name] = tiktoken.get_encoding(encoding_name)
            except Exception as e:
                logger.warning(f"加载tokenizer失败，使用默认: {e}")
                self._tokenizer_cache[encoding_name] = tiktoken.get_encoding("cl100k_base")
        
        return self._tokenizer_cache[encoding_name]
    
    def _calculate_token_savings(self, json_data: dict, toon_data: str, model: str = None) -> tuple:
        """准确计算TOON格式节省的token数
        
        Args:
            json_data: 原始JSON数据（字典）
            toon_data: TOON格式字符串
            model: 模型名称（可选），用于选择对应的tokenizer
            
        Returns:
            (json_tokens, toon_tokens, saved_percent) 元组
        """
        try:
            tokenizer = self._get_tokenizer(model)
            
            # 计算JSON格式的token数
            json_str = json.dumps(json_data, ensure_ascii=False)
            json_tokens = len(tokenizer.encode(json_str))
            
            # 计算TOON格式的token数
            toon_tokens = len(tokenizer.encode(toon_data))
            
            # 计算节省百分比
            saved_percent = ((json_tokens - toon_tokens) / json_tokens) * 100 if json_tokens > 0 else 0
            
            
            return json_tokens, toon_tokens, saved_percent
            
        except Exception as e:
            # 如果tiktoken计算失败，回退到字符数估算
            logger.warning(f"Token精确计算失败，使用字符数估算: {e}")
            json_size = len(json.dumps(json_data, ensure_ascii=False))
            toon_size = len(toon_data)
            saved_percent = ((json_size - toon_size) / json_size) * 100 if json_size > 0 else 0
            return json_size, toon_size, saved_percent

    def _generate_translation_cache_key(self, text: str, source_lang: str, target_lang: str) -> str:
        """生成翻译缓存键的哈希值"""
        cache_string = f"{text}|{source_lang}|{target_lang}"
        return hashlib.md5(cache_string.encode('utf-8')).hexdigest()
    
    def _generate_article_cache_key(self, title: str, content: str, source_lang: str, target_lang: str) -> str:
        """生成文章翻译缓存键的哈希值"""
        cache_string = f"article|{title}|{content[:200]}|{source_lang}|{target_lang}"
        return hashlib.md5(cache_string.encode('utf-8')).hexdigest()

    def _has_articles(self) -> bool:
        """检查系统中是否已有文章数据"""
        try:
            import httpx
            from config import JAVA_BACKEND_URL
            from auth_decorator import get_auth_headers
            
            headers = get_auth_headers()
            headers.update({
                'X-Internal-Service': 'poetize-python',
                'User-Agent': 'poetize-python/1.0.0'
            })
            
            # 请求文章列表，只获取第一页的一条记录来检查是否有数据
            request_data = {
                "pageSize": 1,
                "pageNum": 1,
                "current": 1,
                "size": 1
            }
            
            with httpx.Client(timeout=10) as client:
                response = client.post(
                    f"{JAVA_BACKEND_URL}/article/listArticle",
                    json=request_data,
                    headers=headers
                )
                
                if response.status_code == 200:
                    data = response.json()
                    # 检查是否有文章数据
                    if data.get('code') == 200 and data.get('data'):
                        records = data['data'].get('records', [])
                        total = data['data'].get('total', 0)
                        has_articles = len(records) > 0 or total > 0
                        return has_articles
                    
            return False
            
        except Exception as e:
            logger.error(f"检查文章数据失败: {e}")
            # 出错时为了安全起见，假设有文章数据，阻止修改源语言
            return True

    # _is_cache_valid, _clear_cache_if_file_changed 等文件缓存方法已删除
    # 配置从Java API获取，不再需要文件缓存机制
        """如果文件被修改，主动清除Redis缓存

        参数:
            current_file_mtime: 当前文件的修改时间

        返回:
            bool: 如果清除了缓存返回True，否则返回False
        """
        if not self._is_cache_valid(current_file_mtime):
            try:
                # 检查缓存键是否存在
                config_exists = self.cache_service.exists(self._config_cache_key)
                default_lang_exists = self.cache_service.exists(self._default_lang_cache_key)

                # 文件已修改，清除Redis缓存
                config_deleted = self.cache_service.delete(self._config_cache_key)
                default_lang_deleted = self.cache_service.delete(self._default_lang_cache_key)

                # 改进日志记录：区分"键不存在"和"删除失败"
                config_status = self._get_cache_clear_status(config_exists, config_deleted)
                default_lang_status = self._get_cache_clear_status(default_lang_exists, default_lang_deleted)

                logger.info(f"检测到配置文件修改，主动清除Redis缓存: "
                          f"完整配置={config_status}, "
                          f"默认语言={default_lang_status}")
                return True
            except Exception as e:
                logger.warning(f"主动清除Redis缓存失败: {e}")
                return False
        return False

    def _get_cache_clear_status(self, existed: bool, deleted_count: int) -> str:
        """获取缓存清理状态描述

        参数:
            existed: 缓存键是否存在
            deleted_count: 删除操作返回的计数

        返回:
            str: 状态描述
        """
        if not existed:
            return "键不存在(正常)"
        elif deleted_count > 0:
            return "成功删除"
        else:
            return "删除失败"

    def _parse_llm_config(self, llm_config: dict) -> dict:
        """
        统一解析LLM配置参数，自动为每个提供商设置默认值
        
        Args:
            llm_config: LLM配置字典
            
        Returns:
            包含所有标准化参数的字典（包括提供商特定的默认值）
        """
        # 预先获取interface_type和model用于判断提供商
        interface_type = llm_config.get('interface_type', 'auto')
        model = llm_config.get('model', '')
        
        # 各提供商的默认配置
        provider_defaults = {
            'openai': {
                'api_url': 'https://api.openai.com/v1',
                'model': 'gpt-3.5-turbo'
            },
            'anthropic': {
                'api_url': 'https://api.anthropic.com',
                'model': 'claude-3-5-sonnet-20241022'
            },
            'siliconflow': {
                'api_url': 'https://api.siliconflow.cn/v1/chat/completions',
                'model': 'Qwen/Qwen3-8B'
            },
            'deepseek': {
                'api_url': 'https://api.deepseek.com/v1',
                'model': 'deepseek-chat'
            },
            'custom': {
                'api_url': None,
                'model': 'gpt-3.5-turbo'
            }
        }
        
        # 检测提供商类型
        provider = None
        if interface_type != 'auto':
            provider = interface_type
        else:
            # 自动检测（基于model名称）
            model_lower = model.lower() if model else ''
            if any(x in model_lower for x in ['gpt', 'openai']):
                provider = 'openai'
            elif any(x in model_lower for x in ['claude', 'anthropic']):
                provider = 'anthropic'
            elif 'deepseek' in model_lower:
                provider = 'deepseek'
            elif any(x in model_lower for x in ['qwen', 'glm', 'llama']):
                provider = 'siliconflow'
            else:
                provider = 'custom'
        
        # 获取对应提供商的默认值
        defaults = provider_defaults.get(provider, provider_defaults['custom'])
        
        return {
            'api_key': llm_config.get('api_key'),
            'api_url': llm_config.get('api_url') or defaults['api_url'],
            'model': llm_config.get('model') or defaults['model'],
            'timeout': llm_config.get('timeout', 60),
            'max_tokens': llm_config.get('max_tokens', 4000),
            'temperature': llm_config.get('temperature', 0.3),
            'interface_type': interface_type,
            'prompt': llm_config.get('prompt'),
            'provider': provider  # 额外返回检测到的提供商类型
        }
    
    def _parse_language_config(self, config: TranslationConfig, request: TranslationRequest) -> tuple:
        """
        统一解析语言配置
        
        Args:
            config: 翻译配置
            request: 翻译请求
            
        Returns:
            (source_lang, target_lang, source_lang_name, target_lang_name)
        """
        from language_utils import get_language_mapping
        lang_map = get_language_mapping()
        
        source_lang = request.source_lang if request.source_lang != 'auto' else config.default_source_lang or 'zh'
        target_lang = request.target_lang or config.default_target_lang or 'en'
        source_lang_name = lang_map.get(source_lang, source_lang)
        target_lang_name = lang_map.get(target_lang, target_lang)
        
        return source_lang, target_lang, source_lang_name, target_lang_name
    
    def _detect_llm_interface_type(self, llm_config: dict) -> str:
        """
        检测LLM接口类型
        
        Args:
            llm_config: 解析后的LLM配置（通过_parse_llm_config得到）
            
        Returns:
            接口类型: 'openai', 'anthropic', 'deepseek', 'siliconflow', 'custom'
        """
        interface_type = llm_config.get('interface_type', 'auto')
        model = (llm_config.get('model') or '').lower()
        
        if interface_type != 'auto':
            return interface_type
        
        # 自动检测
        if any(x in model for x in ['gpt', 'openai']):
            return 'openai'
        elif any(x in model for x in ['claude', 'anthropic']):
            return 'anthropic'
        elif 'deepseek' in model:
            return 'deepseek'
        elif any(x in model for x in ['qwen', 'glm', 'llama']):
            return 'siliconflow'
        else:
            return 'custom'
    
    def load_config(self) -> Optional[TranslationConfig]:
        """
        加载文章AI助手配置（从Java后端统一管理）
        包含：翻译功能、智能摘要、全局AI模型等配置
        """
        try:
            from ai_config_client import get_ai_config_client
            client = get_ai_config_client()
            # 使用新方法名获取文章AI助手配置
            data = client.get_article_ai_config('default')
            
            if data:
                logger.info("从Java后端获取文章AI助手配置成功")
                return TranslationConfig(**data)
            
            # 获取失败，返回None
            logger.warning("Java后端获取文章AI助手配置失败")
            return None
        except Exception as e:
            logger.error(f"加载文章AI助手配置失败: {e}")
            return None
    
    async def translate_text(self, request: TranslationRequest) -> TranslationResponse:
        """执行翻译（自动识别单文本或TOON格式）"""
        # 判断是否为文章TOON翻译（title + content）
        is_article_toon = request.title and request.content
        
        if is_article_toon:
            # 使用TOON格式翻译文章
            return await self._translate_article_toon(request)
        
        # 单文本翻译（原有逻辑）
        if not request.text:
            return TranslationResponse(
                success=False,
                error_message="缺少翻译文本"
            )
        
        # 检查Redis缓存
        cache_helper = get_cache_helper()
        cache_key = self._generate_translation_cache_key(
            request.text,
            request.source_lang,
            request.target_lang
        )
        cached_translation = cache_helper.get_cached_translation(cache_key)

        if cached_translation:
            return TranslationResponse(
                success=True,
                translated_text=cached_translation,
                engine="cache"
            )

        config = self.load_config()
        if not config:
            return TranslationResponse(
                success=False,
                error_message="翻译配置未设置"
            )

        try:
            # 执行翻译
            result = None
            if config.type == "none":
                return TranslationResponse(
                    success=True,
                    translated_text=request.text,
                    engine="none",
                    source_lang=request.source_lang,
                    target_lang=request.target_lang
                )
            elif config.type == "baidu":
                result = await self._baidu_translate(config, request)
            elif config.type == "custom":
                result = await self._custom_api_translate(config, request)
            elif config.type == "llm":
                result = await self._translate_with_llm(config, request)
            elif config.type == "dedicated_llm":
                result = await self._translate_with_dedicated_llm(config, request)
            else:
                return TranslationResponse(
                    success=False,
                    error_message="不支持的翻译类型"
                )

            # 缓存结果
            if result and result.success and result.translated_text:
                cache_helper.cache_translation(cache_key, result.translated_text)

            return result

        except Exception as e:
            logger.error(f"翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"翻译失败: {str(e)}"
            )
    
    async def _translate_article_toon(self, request: TranslationRequest) -> TranslationResponse:
        """使用TOON格式一次性翻译文章标题和内容"""
        config = self.load_config()
        if not config:
            return TranslationResponse(
                success=False,
                error_message="翻译配置未设置"
            )
        
        # 只有LLM类型支持TOON优化
        if config.type not in ["llm", "dedicated_llm"]:
            logger.warning(f"翻译类型 {config.type} 不支持TOON格式，降级为分别翻译")
            return await self._translate_article_fallback(request)
        
        # 选择LLM配置
        raw_llm_config = config.llm if config.type == "llm" else config.translation_llm
        if not raw_llm_config:
            return TranslationResponse(
                success=False,
                error_message="LLM配置不完整"
            )
        
        # 统一解析LLM配置参数
        llm_config = self._parse_llm_config(raw_llm_config)
        
        # 统一解析语言配置
        source_lang, target_lang, source_lang_name, target_lang_name = self._parse_language_config(config, request)
        
        # 编码为TOON格式
        article_data = {
            "article": {
                "title": request.title,
                "content": request.content
            }
        }
        toon_data = toon_encode(article_data)
        
        # 使用tiktoken精确计算token节省
        json_tokens, toon_tokens, token_saved = self._calculate_token_savings(
            article_data, 
            toon_data, 
            llm_config.get('model')
        )
        
        # 使用数据库配置的提示词模板
        default_prompt = f"""将以下TOON格式数据从{source_lang_name}翻译为{target_lang_name}。

规则：
1. 保持TOON格式结构不变（2个空格缩进）
2. 翻译title和content的值
3. 保持Markdown格式
4. 只返回TOON格式数据，不添加任何解释

输入TOON数据：
{toon_data}

请返回翻译后的TOON数据，格式如下：
article:
  title: (翻译后的{target_lang_name}标题)
  content: (翻译后的{target_lang_name}内容)"""

        prompt_template = llm_config.get('prompt')
        if prompt_template:
            # 使用自定义提示词，替换占位符
            prompt = prompt_template.replace('{source_lang}', source_lang_name) \
                                  .replace('{target_lang}', target_lang_name) \
                                  .replace('{toon_data}', toon_data)
        else:
            prompt = default_prompt
        
        full_prompt = prompt
        
        # 检测LLM接口类型
        interface_type = self._detect_llm_interface_type(llm_config)
        
        try:
            if interface_type == 'openai':
                result = await self._openai_translate(config, full_prompt)
            elif interface_type == 'anthropic':
                result = await self._anthropic_translate(config, full_prompt)
            elif interface_type == 'deepseek':
                result = await self._deepseek_translate(config, full_prompt)
            elif interface_type == 'siliconflow':
                result = await self._siliconflow_translate(config, full_prompt)
            else:
                result = await self._custom_llm_translate(config, full_prompt)
            
            if not result.success:
                return result
            
            # 解析TOON结果
            try:
                translated_data = toon_decode(result.translated_text)
                article = translated_data.get('article', {})
            except Exception as decode_error:
                logger.error(f"TOON解析失败: {decode_error}", exc_info=True)
                logger.error(f"LLM返回的原始文本（前500字符）: {result.translated_text[:500]}")
                return TranslationResponse(
                    success=False,
                    error_message=f"TOON格式解析失败: {str(decode_error)}"
                )
            
            logger.info(f"TOON翻译成功！消耗 {toon_tokens} tokens，节省 {token_saved:.1f}%")
            
            return TranslationResponse(
                success=True,
                translated_title=article.get('title'),
                translated_content=article.get('content'),
                source_lang=source_lang,
                target_lang=target_lang,
                engine=f"{result.engine}-toon",
                toon_tokens=toon_tokens,
                token_saved_percent=round(token_saved, 1)
            )
            
        except Exception as e:
            logger.error(f"TOON翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"TOON翻译失败: {str(e)}"
            )
    
    async def translate_text_with_config(self, request: TranslationRequest, config: TranslationConfig) -> TranslationResponse:
        """使用指定配置执行翻译（用于测试，支持TOON格式）"""
        try:
            # 检查配置是否有效
            if not config:
                return TranslationResponse(
                    success=False,
                    error_message="翻译配置无效或为空"
                )
                
            # 判断是否为TOON格式测试
            is_toon = request.title and request.content
            
            if config.type == "none":
                if is_toon:
                    return TranslationResponse(
                        success=True,
                        translated_title=request.title,
                        translated_content=request.content,
                        engine="none",
                        source_lang=request.source_lang,
                        target_lang=request.target_lang
                    )
                else:
                    return TranslationResponse(
                        success=True,
                        translated_text=request.text,
                        engine="none",
                        source_lang=request.source_lang,
                        target_lang=request.target_lang
                    )
            
            # TOON格式测试
            if is_toon and config.type in ["llm", "dedicated_llm"]:
                # 使用TOON格式翻译
                llm_config = config.llm if config.type == "llm" else config.translation_llm
                if not llm_config:
                    return TranslationResponse(
                        success=False,
                        error_message="LLM配置不完整"
                    )
                
                # 调用TOON翻译方法
                return await self._translate_article_toon_with_config(request, config, llm_config)
            
            # 单文本翻译
            result = None
            if config.type == "baidu":
                result = await self._baidu_translate(config, request)
            elif config.type == "custom":
                result = await self._custom_api_translate(config, request)
            elif config.type == "llm":
                result = await self._translate_with_llm(config, request)
            elif config.type == "dedicated_llm":
                result = await self._translate_with_dedicated_llm(config, request)
            else:
                return TranslationResponse(
                    success=False,
                    error_message="不支持的翻译类型"
                )

            return result

        except Exception as e:
            logger.error(f"翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"翻译失败: {str(e)}"
            )
    
    async def _translate_article_toon_with_config(self, request: TranslationRequest, config: TranslationConfig, raw_llm_config: dict) -> TranslationResponse:
        """使用指定配置的TOON格式翻译（用于测试）"""
        # 统一解析LLM配置参数
        llm_config = self._parse_llm_config(raw_llm_config)
        
        # 统一解析语言配置
        source_lang, target_lang, source_lang_name, target_lang_name = self._parse_language_config(config, request)
        
        # 编码为TOON格式
        article_data = {
            "article": {
                "title": request.title,
                "content": request.content
            }
        }
        toon_data = toon_encode(article_data)
        
        # 使用tiktoken精确计算token节省
        json_tokens, toon_tokens, token_saved = self._calculate_token_savings(
            article_data, 
            toon_data, 
            llm_config.get('model')
        )
        
        # 使用数据库配置的提示词
        default_prompt = f"""将以下TOON格式数据从{source_lang_name}翻译为{target_lang_name}。

规则：
1. 保持TOON格式结构不变（2个空格缩进）
2. 翻译title和content的值
3. 保持Markdown格式
4. 只返回TOON格式数据，不添加任何解释

输入TOON数据：
{toon_data}

请返回翻译后的TOON数据，格式如下：
article:
  title: (翻译后的{target_lang_name}标题)
  content: (翻译后的{target_lang_name}内容)"""

        prompt_template = llm_config.get('prompt')
        if prompt_template:
            prompt = prompt_template.replace('{source_lang}', source_lang_name) \
                                  .replace('{target_lang}', target_lang_name) \
                                  .replace('{toon_data}', toon_data)
        else:
            prompt = default_prompt
        
        # 检测LLM接口类型
        interface_type = self._detect_llm_interface_type(llm_config)
        
        try:
            if interface_type == 'openai':
                result = await self._openai_translate(config, prompt)
            elif interface_type == 'anthropic':
                result = await self._anthropic_translate(config, prompt)
            elif interface_type == 'deepseek':
                result = await self._deepseek_translate(config, prompt)
            elif interface_type == 'siliconflow':
                result = await self._siliconflow_translate(config, prompt)
            else:
                result = await self._custom_llm_translate(config, prompt)
            
            if not result.success:
                return result
            
            # 解析TOON结果
            try:
                translated_data = toon_decode(result.translated_text)
                article = translated_data.get('article', {})
            except Exception as decode_error:
                logger.error(f"TOON测试解析失败: {decode_error}", exc_info=True)
                logger.error(f"LLM返回的原始文本（前500字符）: {result.translated_text[:500]}")
                return TranslationResponse(
                    success=False,
                    error_message=f"TOON格式解析失败: {str(decode_error)}"
                )
            
            logger.info(f"TOON测试翻译成功！消耗 {toon_tokens} tokens，节省 {token_saved:.1f}%")
            
            return TranslationResponse(
                success=True,
                translated_title=article.get('title'),
                translated_content=article.get('content'),
                source_lang=source_lang,
                target_lang=target_lang,
                engine=f"{result.engine}-toon",
                toon_tokens=toon_tokens,
                token_saved_percent=round(token_saved, 1)
            )
            
        except Exception as e:
            logger.error(f"TOON测试翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"TOON翻译失败: {str(e)}"
            )
    
    async def _baidu_translate(self, config: TranslationConfig, request: TranslationRequest) -> TranslationResponse:
        """百度翻译"""
        import hashlib
        import random
        import time
        
        baidu_config = config.baidu or {}
        app_id = baidu_config.get('app_id')
        secret = baidu_config.get('app_secret')
        
        if not app_id or not secret:
            return TranslationResponse(
                success=False,
                error_message="百度翻译配置不完整"
            )
        
        # 百度翻译API参数
        query = request.text
        from_lang = request.source_lang if request.source_lang != "auto" else "auto"
        to_lang = request.target_lang
        salt = str(random.randint(32768, 65536))
        
        # 生成签名
        sign_str = app_id + query + salt + secret
        sign = hashlib.md5(sign_str.encode()).hexdigest()
        
        # 请求参数
        params = {
            'q': query,
            'from': from_lang,
            'to': to_lang,
            'appid': app_id,
            'salt': salt,
            'sign': sign
        }
        
        try:
            # 从配置中获取超时时间
            if config.llm:
                parsed_config = self._parse_llm_config(config.llm)
                timeout_seconds = parsed_config['timeout']
            else:
                timeout_seconds = 30
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(
                    'https://fanyi-api.baidu.com/api/trans/vip/translate',
                    data=params
                )
                result = response.json()
                
                if 'trans_result' in result:
                    translated_text = result['trans_result'][0]['dst']
                    return TranslationResponse(
                        success=True,
                        translated_text=translated_text,
                        engine="baidu"
                    )
                else:
                    error_msg = result.get('error_msg', '翻译失败')
                    return TranslationResponse(
                        success=False,
                        error_message=f"百度翻译失败: {error_msg}"
                    )
        except Exception as e:
            return TranslationResponse(
                success=False,
                error_message=f"百度翻译请求失败: {str(e)}"
            )
    
    async def _custom_api_translate(self, config: TranslationConfig, request: TranslationRequest) -> TranslationResponse:
        """自定义API翻译"""
        custom_config = config.custom or {}
        api_url = custom_config.get('api_url')
        api_key = custom_config.get('api_key')
        
        if not api_url:
            return TranslationResponse(
                success=False,
                error_message="自定义API地址未配置"
            )
        
        headers = {
            'Content-Type': 'application/json'
        }
        if api_key:
            headers['Authorization'] = f'Bearer {api_key}'
        
        payload = {
            'text': request.text,
            'source_lang': request.source_lang,
            'target_lang': request.target_lang
        }
        
        try:
            # 从配置中获取超时时间
            if config.llm:
                parsed_config = self._parse_llm_config(config.llm)
                timeout_seconds = parsed_config['timeout']
            else:
                timeout_seconds = 30
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(
                    api_url,
                    json=payload,
                    headers=headers
                )
                result = response.json()
                
                if response.status_code == 200 and 'translated_text' in result:
                    return TranslationResponse(
                        success=True,
                        translated_text=result['translated_text'],
                        engine="custom_api"
                    )
                else:
                    return TranslationResponse(
                        success=False,
                        error_message=result.get('error', '自定义API翻译失败')
                    )
        except Exception as e:
            return TranslationResponse(
                success=False,
                error_message=f"自定义API请求失败: {str(e)}"
            )
    
    async def _translate_with_llm(self, config: TranslationConfig, request: TranslationRequest) -> TranslationResponse:
        """使用大模型翻译（支持TOON格式优化）"""
        raw_llm_config = config.llm or {}
        
        # 统一解析LLM配置
        llm_config = self._parse_llm_config(raw_llm_config)
        
        if not llm_config['api_url'] or not llm_config['api_key']:
            return TranslationResponse(
                success=False,
                error_message="大模型配置不完整"
            )
        
        # 统一解析语言配置
        source_lang, target_lang, source_lang_name, target_lang_name = self._parse_language_config(config, request)
        
        # 检测文本格式
        text_format = 'Markdown' if any(marker in request.text for marker in ['#', '*', '**', '[', ']', '```']) else '纯文本'
        
        # 使用数据库配置的提示词，支持占位符替换
        default_prompt = f"请将以下{source_lang_name}文本翻译为{target_lang_name}，保持原意和格式，只返回翻译结果："
        prompt_template = llm_config.get('prompt') or default_prompt
        
        # 替换占位符
        prompt = prompt_template.replace('{source_lang}', source_lang_name) \
                              .replace('{target_lang}', target_lang_name) \
                              .replace('{format}', text_format)
        
        full_prompt = f"{prompt}\n\n{request.text}"
        
        # 检测LLM接口类型并调用对应的翻译方法
        interface_type = self._detect_llm_interface_type(llm_config)
        
        if interface_type == 'openai':
            return await self._openai_translate(config, full_prompt)
        elif interface_type == 'anthropic':
            return await self._anthropic_translate(config, full_prompt)
        elif interface_type == 'siliconflow':
            return await self._siliconflow_translate(config, full_prompt)
        elif interface_type == 'deepseek':
            return await self._deepseek_translate(config, full_prompt)
        else:
            return await self._custom_llm_translate(config, full_prompt)
    
    async def _openai_translate(self, config: TranslationConfig, prompt: str) -> TranslationResponse:
        """OpenAI翻译（旧版 openai<=1.0.0）"""
        try:
            llm_config = self._parse_llm_config(config.llm or {})
            api_key, api_url, model = llm_config['api_key'], llm_config['api_url'], llm_config['model']
            timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
            
            if not api_key:
                return TranslationResponse(
                    success=False,
                    error_message="OpenAI API密钥未配置"
                )
            
            # 处理API URL：如果是完整路径，去掉/chat/completions等后缀
            # 旧版openai库会自动添加这些路径
            if '/chat/completions' in api_url:
                api_url = api_url.split('/chat/completions')[0]
            elif '/completions' in api_url:
                api_url = api_url.split('/completions')[0]
            
            # 设置全局配置（旧版 openai API）
            openai.api_key = api_key
            openai.api_base = api_url
            openai.request_timeout = timeout_seconds
            
            response = await openai.ChatCompletion.acreate(
                model=model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=max_tokens
            )
            
            if response and response.get('choices'):
                message = response['choices'][0]['message']
                finish_reason = response['choices'][0].get('finish_reason', '')
                translated_text = message.get('content', '').strip()
                
                # 检查是否因为token限制导致内容为空
                if not translated_text:
                    if finish_reason == 'length':
                        return TranslationResponse(
                            success=False,
                            error_message="AI输出为空，原因：Max Tokens不足。建议将Max Tokens增加到1500+。"
                        )
                    else:
                        return TranslationResponse(
                            success=False,
                            error_message="AI返回了空内容，请检查模型配置。"
                        )
                
                return TranslationResponse(
                    success=True,
                    translated_text=translated_text,
                    engine="openai"
                )
            else:
                return TranslationResponse(
                    success=False,
                    error_message="OpenAI返回空响应"
                )
                
        except Exception as e:
            logger.error(f"OpenAI翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"OpenAI翻译失败: {str(e)}"
            )
    
    async def _anthropic_translate(self, config: TranslationConfig, prompt: str) -> TranslationResponse:
        """Anthropic翻译"""
        try:
            llm_config = self._parse_llm_config(config.llm or {})
            api_key, model = llm_config['api_key'], llm_config['model']
            timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
            
            if not api_key:
                return TranslationResponse(
                    success=False,
                    error_message="Anthropic API密钥未配置"
                )
            
            headers = {
                "x-api-key": api_key,
                "Content-Type": "application/json",
                "anthropic-version": "2023-06-01"
            }
            
            payload = {
                "model": model,
                "max_tokens": max_tokens,
                "messages": [
                    {"role": "user", "content": prompt}
                ]
            }
            
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(
                    "https://api.anthropic.com/v1/messages",
                    headers=headers,
                    json=payload
                )
            
            if response.status_code == 200:
                data = response.json()
                if data.get('content') and len(data['content']) > 0:
                    translated_text = data['content'][0].get('text', '').strip()
                    return TranslationResponse(
                        success=True,
                        translated_text=translated_text,
                        engine="anthropic"
                    )
                else:
                    return TranslationResponse(
                        success=False,
                        error_message="Anthropic返回空响应"
                    )
            else:
                error_text = response.text
                return TranslationResponse(
                    success=False,
                    error_message=f"Anthropic API调用失败: {response.status_code} - {error_text}"
                )
                
        except Exception as e:
            logger.error(f"Anthropic翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"Anthropic翻译失败: {str(e)}"
            )
    
    async def _siliconflow_translate(self, config: TranslationConfig, prompt: str) -> TranslationResponse:
        """硅基流动翻译"""
        try:
            llm_config = self._parse_llm_config(config.llm or {})
            api_key, model, api_url = llm_config['api_key'], llm_config['model'], llm_config['api_url']
            timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
            
            if not api_key:
                return TranslationResponse(
                    success=False,
                    error_message="硅基流动 API密钥未配置"
                )
            
            # 自动补全URL：如果URL是基础路径（只包含/v1或/v1/），自动添加/chat/completions
            if api_url.rstrip('/').endswith('/v1'):
                api_url = api_url.rstrip('/') + '/chat/completions'
            
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json"
            }
            
            payload = {
                "model": model,
                "messages": [
                    {"role": "user", "content": prompt}
                ],
                "temperature": 0.3,
                "max_tokens": max_tokens,
                "top_p": 0.7,
                "top_k": 50,
                "frequency_penalty": 0.5
            }
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(api_url, headers=headers, json=payload)
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get('choices'):
                        translated_text = result['choices'][0]['message']['content'].strip()
                        return TranslationResponse(
                            success=True,
                            translated_text=translated_text,
                            engine="siliconflow"
                        )
                    else:
                        return TranslationResponse(
                            success=False,
                            error_message="硅基流动返回空响应"
                        )
                else:
                    error_text = response.text
                    return TranslationResponse(
                        success=False,
                        error_message=f"硅基流动API调用失败: {response.status_code} - {error_text}"
                    )
                
        except Exception as e:
            logger.error(f"硅基流动翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"硅基流动翻译失败: {str(e)}"
            )
    
    async def _deepseek_translate(self, config: TranslationConfig, prompt: str) -> TranslationResponse:
        """DeepSeek翻译（使用旧版 openai API）"""
        try:
            llm_config = self._parse_llm_config(config.llm or {})
            api_key, api_url, model = llm_config['api_key'], llm_config['api_url'], llm_config['model']
            timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
            
            if not api_key:
                return TranslationResponse(
                    success=False,
                    error_message="DeepSeek API密钥未配置"
                )
            
            # 处理API URL：如果是完整路径，去掉/chat/completions等后缀
            # 旧版openai库会自动添加这些路径
            if '/chat/completions' in api_url:
                api_url = api_url.split('/chat/completions')[0]
            elif '/completions' in api_url:
                api_url = api_url.split('/completions')[0]
            
            # 设置全局配置（旧版 openai API）
            openai.api_key = api_key
            openai.api_base = api_url
            openai.request_timeout = timeout_seconds
            
            response = await openai.ChatCompletion.acreate(
                model=model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=max_tokens
            )
            
            if response and response.get('choices'):
                message = response['choices'][0]['message']
                finish_reason = response['choices'][0].get('finish_reason', '')
                translated_text = message.get('content', '').strip()
                
                # 检查是否因为token限制导致内容为空
                if not translated_text:
                    if finish_reason == 'length':
                        return TranslationResponse(
                            success=False,
                            error_message="AI输出为空，原因：Max Tokens不足。建议将Max Tokens增加到1500+。"
                        )
                    else:
                        return TranslationResponse(
                            success=False,
                            error_message="AI返回了空内容，请检查模型配置。"
                        )
                
                return TranslationResponse(
                    success=True,
                    translated_text=translated_text,
                    engine="deepseek"
                )
            else:
                return TranslationResponse(
                    success=False,
                    error_message="DeepSeek返回空响应"
                )
                
        except Exception as e:
            logger.error(f"DeepSeek翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"DeepSeek翻译失败: {str(e)}"
            )
    
    async def _translate_with_dedicated_llm(self, config: TranslationConfig, request: TranslationRequest) -> TranslationResponse:
        """使用翻译独立大模型翻译"""
        # 检查配置中是否有翻译独立LLM配置
        if not hasattr(config, 'translation_llm') or not config.translation_llm:
            return TranslationResponse(
                success=False,
                error_message="翻译独立大模型配置未设置"
            )
        
        raw_llm_config = config.translation_llm
        
        # 统一解析LLM配置
        llm_config = self._parse_llm_config(raw_llm_config)
        
        if not llm_config['api_url'] or not llm_config['api_key']:
            return TranslationResponse(
                success=False,
                error_message="翻译独立大模型配置不完整"
            )
        
        # 统一解析语言配置
        source_lang, target_lang, source_lang_name, target_lang_name = self._parse_language_config(config, request)
        
        # 检测文本格式
        text_format = 'Markdown' if any(marker in request.text for marker in ['#', '*', '**', '[', ']', '```']) else '纯文本'
        
        # 构建翻译提示词
        default_prompt = f"请将以下{source_lang_name}文本翻译为{target_lang_name}，保持原意和格式，只返回翻译结果："
        prompt_template = llm_config.get('prompt') or default_prompt
        
        # 替换占位符
        prompt = prompt_template.replace('{source_lang}', source_lang_name) \
                              .replace('{target_lang}', target_lang_name) \
                              .replace('{format}', text_format)
        
        full_prompt = f"{prompt}\n\n{request.text}"
        
        # 检测LLM接口类型并调用对应的翻译方法（使用独立配置）
        interface_type = self._detect_llm_interface_type(llm_config)
        
        if interface_type == 'openai':
            return await self._openai_translate_with_config(llm_config, full_prompt)
        elif interface_type == 'anthropic':
            return await self._anthropic_translate_with_config(llm_config, full_prompt)
        elif interface_type == 'siliconflow':
            return await self._siliconflow_translate_with_config(llm_config, full_prompt)
        elif interface_type == 'deepseek':
            return await self._deepseek_translate_with_config(llm_config, full_prompt)
        else:
            return await self._custom_llm_translate_with_config(llm_config, full_prompt)
    
    async def _openai_translate_with_config(self, raw_llm_config: dict, prompt: str) -> TranslationResponse:
        """使用指定配置的OpenAI翻译（旧版 openai<=1.0.0）"""
        try:
            llm_config = self._parse_llm_config(raw_llm_config)
            api_key, api_url, model = llm_config['api_key'], llm_config['api_url'], llm_config['model']
            timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
            
            # 处理API URL：如果是完整路径，去掉/chat/completions等后缀
            if '/chat/completions' in api_url:
                api_url = api_url.split('/chat/completions')[0]
            elif '/completions' in api_url:
                api_url = api_url.split('/completions')[0]
            
            # 设置全局配置（旧版 openai API）
            openai.api_key = api_key
            openai.api_base = api_url
            openai.request_timeout = timeout_seconds
            
            response = await openai.ChatCompletion.acreate(
                model=model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=max_tokens
            )
            
            if response and response.get('choices'):
                message = response['choices'][0]['message']
                finish_reason = response['choices'][0].get('finish_reason', '')
                translated_text = message.get('content', '').strip()
                
                # 检查是否因为token限制导致内容为空
                if not translated_text:
                    if finish_reason == 'length':
                        return TranslationResponse(
                            success=False,
                            error_message="AI输出为空，原因：Max Tokens不足。建议将Max Tokens增加到1500+。"
                        )
                    else:
                        return TranslationResponse(
                            success=False,
                            error_message="AI返回了空内容，请检查模型配置。"
                        )
                
                return TranslationResponse(
                    success=True,
                    translated_text=translated_text,
                    engine="dedicated-openai"
                )
            else:
                return TranslationResponse(
                    success=False,
                    error_message="OpenAI返回空响应"
                )
                
        except Exception as e:
            logger.error(f"独立OpenAI翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"独立OpenAI翻译失败: {str(e)}"
            )
    
    async def _anthropic_translate_with_config(self, raw_llm_config: dict, prompt: str) -> TranslationResponse:
        """使用指定配置的Anthropic翻译"""
        try:
            llm_config = self._parse_llm_config(raw_llm_config)
            api_key, model = llm_config['api_key'], llm_config['model']
            timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
            
            headers = {
                "x-api-key": api_key,
                "Content-Type": "application/json",
                "anthropic-version": "2023-06-01"
            }
            
            payload = {
                "model": model,
                "max_tokens": max_tokens,
                "messages": [
                    {"role": "user", "content": prompt}
                ]
            }
            
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(
                    "https://api.anthropic.com/v1/messages",
                    headers=headers,
                    json=payload
                )
            
            if response.status_code == 200:
                data = response.json()
                if data.get('content') and len(data['content']) > 0:
                    translated_text = data['content'][0].get('text', '').strip()
                    return TranslationResponse(
                        success=True,
                        translated_text=translated_text,
                        engine="dedicated-anthropic"
                    )
                else:
                    return TranslationResponse(
                        success=False,
                        error_message="Anthropic返回空响应"
                    )
            else:
                error_text = response.text
                return TranslationResponse(
                    success=False,
                    error_message=f"Anthropic API调用失败: {response.status_code} - {error_text}"
                )
                
        except Exception as e:
            logger.error(f"独立Anthropic翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"独立Anthropic翻译失败: {str(e)}"
            )
    
    async def _siliconflow_translate_with_config(self, raw_llm_config: dict, prompt: str) -> TranslationResponse:
        """使用指定配置的硅基流动翻译"""
        try:
            llm_config = self._parse_llm_config(raw_llm_config)
            api_key, model, api_url = llm_config['api_key'], llm_config['model'], llm_config['api_url']
            timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
            
            # 自动补全URL：如果URL是基础路径（只包含/v1或/v1/），自动添加/chat/completions
            if api_url.rstrip('/').endswith('/v1'):
                api_url = api_url.rstrip('/') + '/chat/completions'
            
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json"
            }
            
            payload = {
                "model": model,
                "messages": [
                    {"role": "user", "content": prompt}
                ],
                "temperature": 0.3,
                "max_tokens": max_tokens,
                "top_p": 0.7,
                "top_k": 50,
                "frequency_penalty": 0.5
            }
            
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(api_url, headers=headers, json=payload)
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get('choices'):
                        translated_text = result['choices'][0]['message']['content'].strip()
                        return TranslationResponse(
                            success=True,
                            translated_text=translated_text,
                            engine="dedicated-siliconflow"
                        )
                    else:
                        return TranslationResponse(
                            success=False,
                            error_message="硅基流动返回空响应"
                        )
                else:
                    error_text = response.text
                    return TranslationResponse(
                        success=False,
                        error_message=f"硅基流动API调用失败: {response.status_code} - {error_text}"
                    )
                
        except Exception as e:
            logger.error(f"独立硅基流动翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"独立硅基流动翻译失败: {str(e)}"
            )
    
    async def _deepseek_translate_with_config(self, raw_llm_config: dict, prompt: str) -> TranslationResponse:
        """使用指定配置的DeepSeek翻译（旧版 openai<=1.0.0）"""
        try:
            llm_config = self._parse_llm_config(raw_llm_config)
            api_key, api_url, model = llm_config['api_key'], llm_config['api_url'], llm_config['model']
            timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
            
            # 处理API URL：如果是完整路径，去掉/chat/completions等后缀
            if '/chat/completions' in api_url:
                api_url = api_url.split('/chat/completions')[0]
            elif '/completions' in api_url:
                api_url = api_url.split('/completions')[0]
            
            # 设置全局配置（旧版 openai API）
            openai.api_key = api_key
            openai.api_base = api_url
            openai.request_timeout = timeout_seconds
            
            response = await openai.ChatCompletion.acreate(
                model=model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=max_tokens
            )
            
            if response and response.get('choices'):
                message = response['choices'][0]['message']
                finish_reason = response['choices'][0].get('finish_reason', '')
                translated_text = message.get('content', '').strip()
                
                # 检查是否因为token限制导致内容为空
                if not translated_text:
                    if finish_reason == 'length':
                        return TranslationResponse(
                            success=False,
                            error_message="AI输出为空，原因：Max Tokens不足。建议将Max Tokens增加到1500+。"
                        )
                    else:
                        return TranslationResponse(
                            success=False,
                            error_message="AI返回了空内容，请检查模型配置。"
                        )
                
                return TranslationResponse(
                    success=True,
                    translated_text=translated_text,
                    engine="dedicated-deepseek"
                )
            else:
                return TranslationResponse(
                    success=False,
                    error_message="DeepSeek返回空响应"
                )
                
        except Exception as e:
            logger.error(f"独立DeepSeek翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"独立DeepSeek翻译失败: {str(e)}"
            )
    
    async def _custom_llm_translate_with_config(self, raw_llm_config: dict, prompt: str) -> TranslationResponse:
        """使用指定配置的自定义大模型翻译"""
        llm_config = self._parse_llm_config(raw_llm_config)
        api_url, api_key, model = llm_config['api_url'], llm_config['api_key'], llm_config['model']
        timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
        
        # 处理API URL：如果没有提供完整路径，自动添加/chat/completions
        # httpx不会自动添加路径，需要确保URL完整
        if api_url and not api_url.endswith('/chat/completions') and not api_url.endswith('/completions'):
            # 统一处理：先去掉末尾斜杠，再添加完整路径
            api_url = api_url.rstrip('/')  # 去掉末尾的斜杠
            api_url = api_url + '/chat/completions'
        
        headers = {
            'Content-Type': 'application/json'
        }
        if api_key:
            headers['Authorization'] = f'Bearer {api_key}'
        
        payload = {
            'model': model,
            'messages': [
                {'role': 'user', 'content': prompt}
            ],
            'temperature': 0.3,
            'max_tokens': max_tokens
        }
        
        try:
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(
                    api_url,
                    json=payload,
                    headers=headers
                )
                result = response.json()
                
                if response.status_code == 200:
                    translated_text = None
                    if 'choices' in result:
                        translated_text = result['choices'][0].get('text', '').strip()
                    elif 'content' in result:
                        translated_text = result['content'].strip()
                    elif 'text' in result:
                        translated_text = result['text'].strip()
                    
                    if translated_text:
                        return TranslationResponse(
                            success=True,
                            translated_text=translated_text,
                            engine="dedicated-custom-llm"
                        )
                
                return TranslationResponse(
                    success=False,
                    error_message="自定义模型响应格式无法解析"
                )
        except Exception as e:
            return TranslationResponse(
                success=False,
                error_message=f"独立自定义模型请求失败: {str(e)}"
            )
    
    async def _custom_llm_translate(self, config: TranslationConfig, prompt: str) -> TranslationResponse:
        """自定义大模型翻译"""
        llm_config = self._parse_llm_config(config.llm or {})
        api_url, api_key, model = llm_config['api_url'], llm_config['api_key'], llm_config['model']
        timeout_seconds, max_tokens = llm_config['timeout'], llm_config['max_tokens']
        
        if not api_url:
            return TranslationResponse(
                success=False,
                error_message="自定义模型API地址未配置"
            )
        
        # 处理API URL：如果没有提供完整路径，自动添加/chat/completions
        if not api_url.endswith('/chat/completions') and not api_url.endswith('/completions'):
            api_url = api_url.rstrip('/') + '/chat/completions'
        
        headers = {
            'Content-Type': 'application/json'
        }
        if api_key:
            headers['Authorization'] = f'Bearer {api_key}'
        
        # 构建请求体
        payload = {
            'model': model,
            'messages': [
                {'role': 'user', 'content': prompt}
            ],
            'temperature': 0.3,
            'max_tokens': max_tokens
        }
        
        try:
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(
                    api_url,
                    json=payload,
                    headers=headers
                )
                result = response.json()
                
                if response.status_code == 200:
                    # 尝试多种可能的响应格式
                    translated_text = None
                    if 'choices' in result:
                        translated_text = result['choices'][0].get('text', '').strip()
                    elif 'content' in result:
                        translated_text = result['content'].strip()
                    elif 'text' in result:
                        translated_text = result['text'].strip()
                    
                    if translated_text:
                        return TranslationResponse(
                            success=True,
                            translated_text=translated_text,
                            engine="custom_llm"
                        )
                
                return TranslationResponse(
                    success=False,
                    error_message="自定义模型响应格式无法解析"
                )
        except Exception as e:
            return TranslationResponse(
                success=False,
                error_message=f"自定义模型请求失败: {str(e)}"
            )

    async def test_translation(self, request: TestTranslationRequest) -> Dict[str, Any]:
        """测试翻译服务（支持单文本和TOON格式）"""
        start_time = time.time()
        
        config = self.load_config()
        if not config:
            return {
                'success': False,
                'error_message': '翻译配置未设置'
            }
        
        # 判断是否为TOON格式测试
        is_toon_test = bool(request.title and request.content)
        
        translation_request = TranslationRequest(
            text=request.text if not is_toon_test else None,
            title=request.title if is_toon_test else None,
            content=request.content if is_toon_test else None,
            source_lang=request.source_lang,
            target_lang=request.target_lang
        )
        
        result = await self.translate(config, translation_request)
        
        processing_time = time.time() - start_time
        
        return {
            'success': result.success,
            'translated_text': result.translated_text,
            'translated_title': result.translated_title,
            'translated_content': result.translated_content,
            'source_lang': result.source_lang,
            'target_lang': result.target_lang,
            'engine': result.engine,
            'toon_tokens': result.toon_tokens,
            'token_saved_percent': result.token_saved_percent,
            'error_message': result.error_message,
            'processing_time': round(processing_time, 2),
            'is_toon': bool(result.translated_title and result.translated_content)
        }
    
    async def generate_summary(self, config: TranslationConfig, request: SummaryRequest, is_test: bool = False) -> SummaryResponse:
        """生成文章摘要（根据summaryMode选择生成方式，支持多语言）
        
        Args:
            config: 翻译配置
            request: 摘要请求
            is_test: 是否为测试模式。测试模式下AI失败不会回退到TextRank，直接返回错误
        """
        import time
        start_time = time.time()
        
        summary_config = config.summary or {}
        summary_mode = summary_config.get('summaryMode', 'global')  # 'global' | 'dedicated' | 'textrank'
        
        # 获取文章的语言内容字典
        if not request.languages:
            # 如果没有提供languages，使用content作为默认源语言
            source_lang = config.default_source_lang or 'zh'
            request.languages = {source_lang: request.content} if request.content else {}
        
        if not request.languages:
            return SummaryResponse(
                success=False,
                error_message="文章内容为空",
                processing_time=round(time.time() - start_time, 2)
            )
        
        # 如果选择TextRank，对每个语言分别调用TextRank
        if summary_mode == 'textrank':
            try:
                textrank_summary = await self._generate_textrank_summary_multilang(request)
                processing_time = time.time() - start_time
                textrank_summary.processing_time = round(processing_time, 2)
                return textrank_summary
            except Exception as e:
                logger.error(f"TextRank摘要生成失败: {e}")
                return SummaryResponse(
                    success=False,
                    error_message=f"TextRank摘要生成失败: {str(e)}",
                    processing_time=round(time.time() - start_time, 2)
                )
        
        # 判断使用哪个AI配置
        llm_config = None
        
        if summary_mode == 'dedicated' and summary_config.get('dedicated_llm'):
            # 使用独立AI配置
            llm_config = summary_config['dedicated_llm']
        elif config.llm:
            # 使用全局AI配置
            llm_config = config.llm
        
        if llm_config:
            try:
                # 尝试使用AI生成摘要（多语言）
                ai_summary = await self._generate_ai_summary_multilang(config, request, llm_config)
                if ai_summary.success:
                    processing_time = time.time() - start_time
                    ai_summary.processing_time = round(processing_time, 2)
                    return ai_summary
                else:
                    # 测试模式：直接返回AI错误，不回退
                    if is_test:
                        processing_time = time.time() - start_time
                        ai_summary.processing_time = round(processing_time, 2)
                        return ai_summary
                    # 生产模式：回退到TextRank
                    logger.warning(f"AI摘要生成失败，回退到TextRank: {ai_summary.error_message}")
            except Exception as e:
                # 测试模式：直接返回异常错误，不回退
                if is_test:
                    logger.error(f"AI摘要生成异常: {e}")
                    return SummaryResponse(
                        success=False,
                        error_message=f"AI摘要生成异常: {str(e)}",
                        processing_time=round(time.time() - start_time, 2)
                    )
                # 生产模式：回退到TextRank
                logger.error(f"AI摘要生成异常，回退到TextRank: {e}")
        
        # 回退到TextRank算法（多语言）
        try:
            textrank_summary = await self._generate_textrank_summary_multilang(request)
            processing_time = time.time() - start_time
            textrank_summary.processing_time = round(processing_time, 2)
            return textrank_summary
        except Exception as e:
            logger.error(f"TextRank摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"摘要生成失败: {str(e)}",
                processing_time=round(time.time() - start_time, 2)
            )
    
    async def _generate_ai_summary_multilang(self, config: TranslationConfig, request: SummaryRequest, llm_config: dict = None) -> SummaryResponse:
        """使用AI生成多语言摘要（使用TOON格式优化，预计节省15-25% token）"""
        # 如果未传入llm_config，则使用config中的
        if llm_config is None:
            llm_config = config.llm or {}
        summary_config = config.summary or {}
        
        # 构建多语言摘要提示词
        style_prompts = {
            'concise': '简洁明了，突出文章的核心观点',
            'detailed': '详细全面，包含文章的主要内容和关键信息',
            'academic': '学术风格，使用专业术语和结构化表达'
        }
        
        style_desc = style_prompts.get(request.style, style_prompts['concise'])
        
        # 使用统一的语言映射
        from language_utils import get_language_mapping
        lang_names = get_language_mapping()
        
        # 获取源语言配置
        source_lang_code = config.default_source_lang or 'zh'
        
        # 如果源语言不在languages中，使用第一个可用的语言
        if source_lang_code not in request.languages:
            source_lang_code = list(request.languages.keys())[0]
        
        source_content = request.languages[source_lang_code]
        source_lang_name = lang_names.get(source_lang_code, source_lang_code)
        
        # 构建需要生成的语言列表
        target_lang_names = []
        for lang_code in request.languages.keys():
            lang_name = lang_names.get(lang_code, lang_code)
            target_lang_names.append(lang_name)
        
        languages_str = "、".join(target_lang_names)
        
        # 使用TOON格式优化token消耗
        # 构建示例摘要结构
        example_summaries = {}
        for lang_code in request.languages.keys():
            lang_name = lang_names.get(lang_code, lang_code)
            example_summaries[lang_code] = f"{lang_name}摘要内容"
        
        # 编码为TOON格式
        summary_data = {"summaries": example_summaries}
        toon_example = toon_encode(summary_data)
        
        # 先解析LLM配置（用于获取模型名称和其他参数）
        parsed_llm_config = self._parse_llm_config(llm_config)
        model_name = parsed_llm_config.get('model')
        timeout_seconds = parsed_llm_config['timeout']
        
        # 使用tiktoken精确计算token节省
        json_tokens, toon_tokens, token_saved = self._calculate_token_savings(
            summary_data, 
            toon_example, 
            model_name
        )
        
        # 构建提示词（使用TOON格式）
        default_prompt = f"""请为以下{source_lang_name}文章生成多语言摘要，要求：
1. 生成语言：{languages_str}
2. 风格：{style_desc}
3. 每个语言的摘要长度控制在{request.max_length}字符以内
4. 保持TOON格式结构不变（2个空格缩进）
5. 只返回TOON格式数据，不添加任何解释或markdown代码块标记
6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）

文章内容：

{source_content}

请返回TOON格式的摘要，格式如下：
{toon_example}"""
        
        # 获取配置的提示词模板
        custom_prompt_template = summary_config.get('prompt')
        
        if custom_prompt_template:
            # 使用自定义提示词，支持占位符替换
            prompt = custom_prompt_template.replace('{style_desc}', style_desc) \
                                           .replace('{max_length}', str(request.max_length)) \
                                           .replace('{toon_example}', toon_example) \
                                           .replace('{content_text}', source_content) \
                                           .replace('{source_content}', source_content) \
                                           .replace('{languages}', languages_str) \
                                           .replace('{source_lang}', source_lang_name)
        else:
            prompt = default_prompt
        
        # 检测LLM接口类型并调用对应的摘要生成方法
        
        interface_type = self._detect_llm_interface_type(parsed_llm_config)
        
        if interface_type == 'openai':
            return await self._openai_summary_multilang(config, prompt, timeout_seconds, request.languages.keys(), llm_config, token_saved, toon_tokens)
        elif interface_type == 'anthropic':
            return await self._anthropic_summary_multilang(config, prompt, timeout_seconds, request.languages.keys(), llm_config, token_saved, toon_tokens)
        elif interface_type == 'siliconflow':
            return await self._siliconflow_summary_multilang(config, prompt, timeout_seconds, request.languages.keys(), llm_config, token_saved, toon_tokens)
        elif interface_type == 'deepseek':
            return await self._deepseek_summary_multilang(config, prompt, timeout_seconds, request.languages.keys(), llm_config, token_saved, toon_tokens)
        else:
            return await self._custom_llm_summary_multilang(config, prompt, timeout_seconds, request.languages.keys(), llm_config, token_saved, toon_tokens)
    
    async def _openai_summary_multilang(self, config: TranslationConfig, prompt: str, timeout_seconds: int = 30, languages: list = None, llm_config: dict = None, token_saved: float = 0, toon_tokens: int = 0) -> SummaryResponse:
        """OpenAI多语言摘要生成（使用TOON格式优化，旧版 openai<=1.0.0）"""
        try:
            # 使用传入的llm_config，如果没有则使用全局配置
            if llm_config is None:
                llm_config = config.llm or {}
            parsed_config = self._parse_llm_config(llm_config)
            api_key, api_url, model = parsed_config['api_key'], parsed_config['api_url'], parsed_config['model']
            
            if not api_key:
                return SummaryResponse(
                    success=False,
                    error_message="OpenAI API密钥未配置"
                )
            
            # 处理API URL：如果是完整路径，去掉/chat/completions等后缀
            if '/chat/completions' in api_url:
                api_url = api_url.split('/chat/completions')[0]
            elif '/completions' in api_url:
                api_url = api_url.split('/completions')[0]
            
            # 设置全局配置（旧版 openai API）
            openai.api_key = api_key
            openai.api_base = api_url
            openai.request_timeout = timeout_seconds
            
            max_tokens = llm_config.get('max_tokens', 1000)
            
            response = await openai.ChatCompletion.acreate(
                model=model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=max_tokens
            )
            
            if response and response.get('choices'):
                message = response['choices'][0]['message']
                finish_reason = response['choices'][0].get('finish_reason', '')
                
                # 优先使用content，如果为空则尝试reasoning_content（适配GLM-4等思考模型）
                summary_text = message.get('content', '').strip()
                if not summary_text and 'reasoning_content' in message:
                    summary_text = message.get('reasoning_content', '').strip()
                
                # 检查是否因为token限制导致内容为空
                if not summary_text:
                    if finish_reason == 'length':
                        return SummaryResponse(
                            success=False,
                            error_message="AI输出为空，原因：思考模型的思考过程占用了所有tokens。建议：1) 将Max Tokens增加到2000+，2) 或使用普通模型而非思考模型。"
                        )
                    else:
                        return SummaryResponse(
                            success=False,
                            error_message="AI返回了空内容，请检查模型配置或尝试增加Max Tokens。"
                        )
                
                # 解析TOON格式的摘要
                return self._parse_toon_summary(summary_text, token_saved, toon_tokens, "ai-openai")
            else:
                return SummaryResponse(
                    success=False,
                    error_message="OpenAI返回空响应"
                )
                
        except Exception as e:
            logger.error(f"OpenAI多语言摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"OpenAI多语言摘要生成失败: {str(e)}"
            )
    
    async def _anthropic_summary_multilang(self, config: TranslationConfig, prompt: str, timeout_seconds: int = 30, languages: list = None, llm_config: dict = None, token_saved: float = 0, toon_tokens: int = 0) -> SummaryResponse:
        """Anthropic多语言摘要生成（使用TOON格式优化）"""
        try:
            # 使用传入的llm_config，如果没有则使用全局配置
            if llm_config is None:
                llm_config = config.llm or {}
            parsed_config = self._parse_llm_config(llm_config)
            api_key, model = parsed_config['api_key'], parsed_config['model']
            
            if not api_key:
                return SummaryResponse(
                    success=False,
                    error_message="Anthropic API密钥未配置"
                )
            
            headers = {
                "x-api-key": api_key,
                "Content-Type": "application/json",
                "anthropic-version": "2023-06-01"
            }
            
            max_tokens = llm_config.get('max_tokens', 1000)
            
            payload = {
                "model": model,
                "max_tokens": max_tokens,
                "messages": [
                    {"role": "user", "content": prompt}
                ]
            }
            
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(
                    "https://api.anthropic.com/v1/messages",
                    headers=headers,
                    json=payload
                )
            
            if response.status_code == 200:
                data = response.json()
                if data.get('content') and len(data['content']) > 0:
                    summary_text = data['content'][0].get('text', '').strip()
                    # 解析TOON格式的摘要
                    return self._parse_toon_summary(summary_text, token_saved, toon_tokens, "ai-anthropic")
                else:
                    return SummaryResponse(
                        success=False,
                        error_message="Anthropic返回空响应"
                    )
            else:
                error_text = response.text
                return SummaryResponse(
                    success=False,
                    error_message=f"Anthropic API调用失败: {response.status_code} - {error_text}"
                )
                
        except Exception as e:
            logger.error(f"Anthropic多语言摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"Anthropic多语言摘要生成失败: {str(e)}"
            )
    
    async def _siliconflow_summary_multilang(self, config: TranslationConfig, prompt: str, timeout_seconds: int = 30, languages: list = None, llm_config: dict = None, token_saved: float = 0, toon_tokens: int = 0) -> SummaryResponse:
        """硅基流动多语言摘要生成（使用TOON格式优化）"""
        try:
            # 使用传入的llm_config，如果没有则使用全局配置
            if llm_config is None:
                llm_config = config.llm or {}
            parsed_config = self._parse_llm_config(llm_config)
            api_key, model, api_url = parsed_config['api_key'], parsed_config['model'], parsed_config['api_url']
            
            if not api_key:
                return SummaryResponse(
                    success=False,
                    error_message="硅基流动 API密钥未配置"
                )
            
            # 自动补全URL：如果URL是基础路径（只包含/v1或/v1/），自动添加/chat/completions
            if api_url.rstrip('/').endswith('/v1'):
                api_url = api_url.rstrip('/') + '/chat/completions'
            
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json"
            }
            
            max_tokens = llm_config.get('max_tokens', 1000)
            
            payload = {
                "model": model,
                "messages": [
                    {"role": "user", "content": prompt}
                ],
                "temperature": 0.3,
                "max_tokens": max_tokens,
                "top_p": 0.7,
                "top_k": 50,
                "frequency_penalty": 0.5
            }
            
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(api_url, headers=headers, json=payload)
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get('choices'):
                        summary_text = result['choices'][0]['message']['content'].strip()
                        # 解析TOON格式的摘要
                        return self._parse_toon_summary(summary_text, token_saved, toon_tokens, "ai-siliconflow")
                    else:
                        return SummaryResponse(
                            success=False,
                            error_message="硅基流动返回空响应"
                        )
                else:
                    error_text = response.text
                    return SummaryResponse(
                        success=False,
                        error_message=f"硅基流动API调用失败: {response.status_code} - {error_text}"
                    )
                
        except Exception as e:
            logger.error(f"硅基流动多语言摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"硅基流动多语言摘要生成失败: {str(e)}"
            )
    
    async def _deepseek_summary_multilang(self, config: TranslationConfig, prompt: str, timeout_seconds: int = 30, languages: list = None, llm_config: dict = None, token_saved: float = 0, toon_tokens: int = 0) -> SummaryResponse:
        """DeepSeek多语言摘要生成（使用TOON格式优化，旧版 openai<=1.0.0）"""
        try:
            # 使用传入的llm_config，如果没有则使用全局配置
            if llm_config is None:
                llm_config = config.llm or {}
            parsed_config = self._parse_llm_config(llm_config)
            api_key, api_url, model = parsed_config['api_key'], parsed_config['api_url'], parsed_config['model']
            
            if not api_key:
                return SummaryResponse(
                    success=False,
                    error_message="DeepSeek API密钥未配置"
                )
            
            # 处理API URL：如果是完整路径，去掉/chat/completions等后缀
            if '/chat/completions' in api_url:
                api_url = api_url.split('/chat/completions')[0]
            elif '/completions' in api_url:
                api_url = api_url.split('/completions')[0]
            
            # 设置全局配置（旧版 openai API）
            openai.api_key = api_key
            openai.api_base = api_url
            openai.request_timeout = timeout_seconds
            
            max_tokens = llm_config.get('max_tokens', 1000)
            
            response = await openai.ChatCompletion.acreate(
                model=model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=max_tokens
            )
            
            if response and response.get('choices'):
                message = response['choices'][0]['message']
                finish_reason = response['choices'][0].get('finish_reason', '')
                
                # 优先使用content，如果为空则尝试reasoning_content
                summary_text = message.get('content', '').strip()
                if not summary_text and 'reasoning_content' in message:
                    summary_text = message.get('reasoning_content', '').strip()
                
                # 检查是否因为token限制导致内容为空
                if not summary_text:
                    if finish_reason == 'length':
                        return SummaryResponse(
                            success=False,
                            error_message="AI输出为空，原因：思考模型的思考过程占用了所有tokens。建议：1) 将Max Tokens增加到2000+，2) 或使用普通模型而非思考模型。"
                        )
                    else:
                        return SummaryResponse(
                            success=False,
                            error_message="AI返回了空内容，请检查模型配置或尝试增加Max Tokens。"
                        )
                
                # 解析TOON格式的摘要
                return self._parse_toon_summary(summary_text, token_saved, toon_tokens, "ai-deepseek")
            else:
                return SummaryResponse(
                    success=False,
                    error_message="DeepSeek返回空响应"
                )
                
        except Exception as e:
            logger.error(f"DeepSeek多语言摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"DeepSeek多语言摘要生成失败: {str(e)}"
            )
    
    async def _custom_llm_summary_multilang(self, config: TranslationConfig, prompt: str, timeout_seconds: int = 30, languages: list = None, llm_config: dict = None, token_saved: float = 0, toon_tokens: int = 0) -> SummaryResponse:
        """自定义大模型多语言摘要生成（使用TOON格式优化）"""
        # 使用传入的llm_config，如果没有则使用全局配置
        if llm_config is None:
            llm_config = config.llm or {}
        parsed_config = self._parse_llm_config(llm_config)
        api_url, api_key, model = parsed_config['api_url'], parsed_config['api_key'], parsed_config['model']
        
        if not api_url:
            return SummaryResponse(
                success=False,
                error_message="自定义模型API地址未配置"
            )
        
        # 处理API URL：如果没有提供完整路径，自动添加/chat/completions
        # httpx不会自动添加路径，需要确保URL完整
        if not api_url.endswith('/chat/completions') and not api_url.endswith('/completions'):
            # 统一处理：先去掉末尾斜杠，再添加完整路径
            api_url = api_url.rstrip('/')  # 去掉末尾的斜杠
            api_url = api_url + '/chat/completions'
        
        headers = {
            'Content-Type': 'application/json'
        }
        if api_key:
            headers['Authorization'] = f'Bearer {api_key}'
        
        max_tokens, timeout_seconds = parsed_config['max_tokens'], parsed_config['timeout']
        
        payload = {
            'model': model,
            'messages': [
                {'role': 'user', 'content': prompt}
            ],
            'temperature': 0.3,
            'max_tokens': max_tokens,
        }
        
        try:
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(api_url, json=payload, headers=headers)
                response.raise_for_status()
                
                data = response.json()
                if 'choices' in data and data['choices']:
                    finish_reason = data['choices'][0].get('finish_reason', '')
                    
                    if 'message' in data['choices'][0]:
                        summary_text = data['choices'][0]['message']['content'].strip()
                    else:
                        # 有些API返回格式是 text 而不是 message
                        summary_text = data['choices'][0].get('text', '').strip()
                    
                    # 检查是否因为token限制导致内容为空
                    if not summary_text:
                        if finish_reason == 'length':
                            return SummaryResponse(
                                success=False,
                                error_message="AI输出为空，原因：思考模型的思考过程占用了所有tokens。建议：1) 将Max Tokens增加到2000+，2) 或使用普通模型而非思考模型。"
                            )
                        else:
                            return SummaryResponse(
                                success=False,
                                error_message="AI返回了空内容，请检查模型配置或尝试增加Max Tokens。"
                            )
                    
                    # 解析TOON格式的摘要
                    return self._parse_toon_summary(summary_text, token_saved, toon_tokens, "ai-custom")
                else:
                    return SummaryResponse(
                        success=False,
                        error_message="自定义模型返回格式错误"
                    )
                    
        except Exception as e:
            logger.error(f"自定义LLM多语言摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"自定义LLM多语言摘要生成失败: {str(e)}"
            )
    
    def _parse_toon_summary(self, summary_text: str, token_saved: float, toon_tokens: int, method: str) -> SummaryResponse:
        """解析AI返回的TOON格式摘要（通用方法）
        
        Args:
            summary_text: AI返回的TOON格式文本
            token_saved: 使用TOON格式节省的token百分比
            toon_tokens: TOON格式消耗的token数
            method: 使用的方法标识（如 "ai-openai", "ai-anthropic" 等）
            
        Returns:
            SummaryResponse对象，包含解析结果或错误信息
        """
        try:
            decoded_data = toon_decode(summary_text)
            summaries = decoded_data.get('summaries', {})
            if summaries and isinstance(summaries, dict):
                logger.info(f"TOON摘要生成成功！消耗 {toon_tokens} tokens，节省 {token_saved:.1f}% (method: {method})")
                return SummaryResponse(
                    success=True,
                    summaries=summaries,
                    method=method,
                    toon_tokens=toon_tokens,
                    token_saved_percent=round(token_saved, 1)
                )
            else:
                return SummaryResponse(
                    success=False,
                    error_message="TOON格式解析失败：summaries字段不存在或格式错误"
                )
        except Exception as decode_error:
            logger.error(f"TOON格式解析失败: {decode_error}", exc_info=True)
            logger.error(f"LLM返回的原始文本（前500字符）: {summary_text[:500]}")
            return SummaryResponse(
                success=False,
                error_message=f"TOON格式解析失败: {str(decode_error)}"
            )
    
    async def _generate_textrank_summary_multilang(self, request: SummaryRequest) -> SummaryResponse:
        """对每个语言分别调用Java后端的TextRank摘要生成"""
        try:
            summaries = {}
            
            # 对每个语言的内容分别生成摘要
            for lang_code, content in request.languages.items():
                if not content or not content.strip():
                    continue
                
                payload = {
                    'content': content,
                    'maxLength': request.max_length
                }
                
                async with httpx.AsyncClient(timeout=15) as client:
                    response = await client.post(
                        f"{JAVA_BACKEND_URL}/article/generateSummary",
                        json=payload,
                        headers={'Content-Type': 'application/json'}
                    )
                    response.raise_for_status()
                    
                    data = response.json()
                    if data.get('code') == 200 and data.get('data'):
                        summaries[lang_code] = data['data']
                    else:
                        logger.error(f"TextRank摘要生成失败: {lang_code}")
                        return SummaryResponse(
                            success=False,
                            error_message=f"{lang_code}语言TextRank摘要生成失败: {data.get('message', '未知错误')}"
                        )
            
            if summaries:
                return SummaryResponse(
                    success=True,
                    summaries=summaries,
                    method="textrank"
                )
            else:
                return SummaryResponse(
                    success=False,
                    error_message="所有语言的TextRank摘要生成都失败"
                )
                    
        except Exception as e:
            logger.error(f"TextRank多语言摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"TextRank多语言摘要生成失败: {str(e)}"
            )
    
    async def _translate_article_fallback(self, request: TranslationRequest) -> TranslationResponse:
        """降级方案：分别翻译标题和内容（用于不支持TOON的翻译类型）"""
        logger.warning("使用降级方案分别翻译标题和内容")
        
        try:
            # 翻译标题
            title_req = TranslationRequest(
                text=request.title,
                source_lang=request.source_lang,
                target_lang=request.target_lang
            )
            title_result = await self.translate_text(title_req)
            
            if not title_result.success:
                return TranslationResponse(
                    success=False,
                    error_message=f"标题翻译失败: {title_result.error_message}"
                )
            
            # 翻译内容
            content_req = TranslationRequest(
                text=request.content,
                source_lang=request.source_lang,
                target_lang=request.target_lang
            )
            content_result = await self.translate_text(content_req)
            
            if not content_result.success:
                return TranslationResponse(
                    success=False,
                    error_message=f"内容翻译失败: {content_result.error_message}"
                )
            
            return TranslationResponse(
                success=True,
                translated_title=title_result.translated_text,
                translated_content=content_result.translated_text,
                source_lang=request.source_lang,
                target_lang=request.target_lang,
                engine="fallback-double",
                token_saved_percent=0.0
            )
            
        except Exception as e:
            logger.error(f"降级翻译失败: {e}", exc_info=True)
            return TranslationResponse(
                success=False,
                error_message=f"降级翻译失败: {str(e)}"
            )
    
# 创建翻译管理器实例
translation_manager = TranslationManager()

def register_translation_api(app):
    """注册翻译API路由"""
    
    @app.post("/api/translation/test-connection")
    async def test_connection(request: TestTranslationRequest, admin_user = Depends(admin_required)):
        """快速测试AI连接（不执行完整翻译）"""
        try:
            # 优先使用前端传来的临时配置
            if request.config:
                try:
                    # 先加载已保存的配置（用于获取密钥）
                    saved_config = translation_manager.load_config()
                    
                    # 合并配置
                    merged_config_dict = request.config.copy()
                    
                    # 合并密钥
                    if saved_config:
                        if merged_config_dict.get('llm') and not merged_config_dict['llm'].get('api_key'):
                            if saved_config.llm and saved_config.llm.get('api_key'):
                                merged_config_dict['llm']['api_key'] = saved_config.llm['api_key']
                        
                        if merged_config_dict.get('translation_llm') and not merged_config_dict['translation_llm'].get('api_key'):
                            if saved_config.translation_llm and saved_config.translation_llm.get('api_key'):
                                merged_config_dict['translation_llm']['api_key'] = saved_config.translation_llm['api_key']
                    
                    config = TranslationConfig(**merged_config_dict)
                except Exception as e:
                    logger.error(f"解析配置失败: {e}")
                    return {
                        'code': 400,
                        'message': f'配置格式错误: {str(e)}'
                    }
            else:
                config = translation_manager.load_config()
                if not config:
                    return {
                        'code': 500,
                        'message': '无法连接到Java后端获取配置，请检查后端服务是否正常运行'
                    }
            
            # 使用极简单的测试文本进行快速测试
            test_request = TranslationRequest(
                text="Hi",
                source_lang='en',
                target_lang='zh'
            )
            
            # 执行快速翻译测试
            result = await translation_manager.translate_text_with_config(test_request, config)
            
            if result.success:
                return {
                    'code': 200,
                    'message': '连接成功',
                    'data': {
                        'engine': result.engine,
                        'test_result': result.translated_text
                    }
                }
            else:
                return {
                    'code': 500,
                    'message': result.error_message or '连接测试失败'
                }
                
        except Exception as e:
            logger.error(f"连接测试失败: {str(e)}")
            return {
                'code': 500,
                'message': f'连接测试失败: {str(e)}'
            }
    
    @app.post("/api/translation/test")
    async def test_translation(request: TestTranslationRequest, admin_user = Depends(admin_required)):
        """测试翻译（支持临时配置）"""
        try:
            # 优先使用前端传来的临时配置，但需要合并已保存配置中的密钥
            if request.config:
                try:
                    # 先加载已保存的配置（用于获取密钥）
                    saved_config = translation_manager.load_config()
                    
                    # 合并配置：临时配置 + 已保存配置中的密钥
                    merged_config_dict = request.config.copy()
                    
                    # 如果临时配置中没有api_key，从已保存配置中获取
                    if saved_config:
                        # 合并 llm 配置中的 api_key
                        if merged_config_dict.get('llm') and not merged_config_dict['llm'].get('api_key'):
                            if saved_config.llm and saved_config.llm.get('api_key'):
                                merged_config_dict['llm']['api_key'] = saved_config.llm['api_key']
                        
                        # 合并 translation_llm 配置中的 api_key
                        if merged_config_dict.get('translation_llm') and not merged_config_dict['translation_llm'].get('api_key'):
                            if saved_config.translation_llm and saved_config.translation_llm.get('api_key'):
                                merged_config_dict['translation_llm']['api_key'] = saved_config.translation_llm['api_key']
                        
                        # 合并 baidu 配置中的 app_secret
                        if merged_config_dict.get('baidu') and not merged_config_dict['baidu'].get('app_secret'):
                            if saved_config.baidu and saved_config.baidu.get('app_secret'):
                                merged_config_dict['baidu']['app_secret'] = saved_config.baidu['app_secret']
                        
                        # 合并 custom 配置中的密钥
                        if merged_config_dict.get('custom'):
                            if not merged_config_dict['custom'].get('api_key') and saved_config.custom and saved_config.custom.get('api_key'):
                                merged_config_dict['custom']['api_key'] = saved_config.custom['api_key']
                            if not merged_config_dict['custom'].get('app_secret') and saved_config.custom and saved_config.custom.get('app_secret'):
                                merged_config_dict['custom']['app_secret'] = saved_config.custom['app_secret']
                        
                        # 合并 summary 配置中的 dedicated_llm api_key
                        if merged_config_dict.get('summary') and merged_config_dict['summary'].get('dedicated_llm'):
                            if not merged_config_dict['summary']['dedicated_llm'].get('api_key'):
                                if saved_config.summary and saved_config.summary.get('dedicated_llm') and saved_config.summary['dedicated_llm'].get('api_key'):
                                    merged_config_dict['summary']['dedicated_llm']['api_key'] = saved_config.summary['dedicated_llm']['api_key']
                    
                    config = TranslationConfig(**merged_config_dict)
                except Exception as e:
                    logger.error(f"解析前端配置失败: {e}")
                    return {
                        'code': 400,
                        'message': f'配置格式错误: {str(e)}'
                    }
            else:
                config = translation_manager.load_config()
                if not config:
                    return {
                        'code': 500,
                        'message': '无法连接到Java后端获取配置，请检查后端服务是否正常运行'
                    }
            
            # 判断是否为TOON格式测试（有title和content说明是TOON测试）
            is_toon_test = bool(request.title and request.content)
            
            # 使用配置中的默认语言创建翻译请求
            translation_request = TranslationRequest(
                text=request.text if not is_toon_test else None,
                title=request.title if is_toon_test else None,
                content=request.content if is_toon_test else None,
                source_lang=getattr(config, 'default_source_lang', 'zh'),
                target_lang=getattr(config, 'default_target_lang', 'en')
            )
            
            # 执行翻译
            result = await translation_manager.translate_text_with_config(translation_request, config)
            
            if result.success:
                data = {
                    'engine': result.engine,
                    'source_lang': translation_request.source_lang,
                    'target_lang': translation_request.target_lang
                }
                
                # 单文本翻译结果
                if result.translated_text:
                    data['translated_text'] = result.translated_text
                
                # TOON格式翻译结果
                if result.translated_title and result.translated_content:
                    data['translated_title'] = result.translated_title
                    data['translated_content'] = result.translated_content
                    data['is_toon'] = True
                    if result.toon_tokens:
                        data['toon_tokens'] = result.toon_tokens
                    if result.token_saved_percent:
                        data['token_saved_percent'] = result.token_saved_percent
                else:
                    data['is_toon'] = False
                
                return {
                    'code': 200,
                    'message': '翻译成功',
                    'data': data
                }
            else:
                return {
                    'code': 400,
                    'message': result.error_message
                }
        except Exception as e:
            logger.error(f"测试翻译失败: {e}", exc_info=True)
            return {
                'code': 500,
                'message': f'翻译测试失败: {str(e)}'
            }
    
    @app.post("/api/translation/translate")
    async def translate_text_api(request: TranslationRequest, _: bool = Depends(admin_required)):
        """翻译文本API（支持单文本和TOON格式文章翻译）"""
        try:
            result = await translation_manager.translate_text(request)
            if result.success:
                data = {'engine': result.engine}
                
                # 单文本翻译
                if result.translated_text:
                    data['translated_text'] = result.translated_text
                
                # TOON格式文章翻译
                if result.translated_title and result.translated_content:
                    data['translated_title'] = result.translated_title
                    data['translated_content'] = result.translated_content
                    if result.token_saved_percent:
                        data['token_saved_percent'] = result.token_saved_percent
                
                return {
                    'code': 200,
                    'message': '翻译成功',
                    'data': data
                }
            else:
                return {
                    'code': 400,
                    'message': result.error_message
                }
        except Exception as e:
            logger.error(f"翻译失败: {e}", exc_info=True)
            return {
                'code': 500,
                'message': f'翻译失败: {str(e)}'
            }
    
    @app.post("/api/translation/generate-summary")
    async def generate_summary_api(request: SummaryRequest, admin_user = Depends(admin_required)):
        """生成文章摘要（多语言）"""
        try:
            config = translation_manager.load_config()
            if not config:
                return {
                    'code': 400,
                    'message': '翻译配置未设置'
                }
            
            result = await translation_manager.generate_summary(config, request)
            
            if result.success:
                return {
                    'code': 200,
                    'message': '摘要生成成功',
                    'data': {
                        'summaries': result.summaries,
                        'method': result.method,
                        'processing_time': result.processing_time
                    }
                }
            else:
                return {
                    'code': 500,
                    'message': result.error_message or '摘要生成失败'
                }
                
        except Exception as e:
            logger.error(f"摘要生成API失败: {e}")
            return {
                'code': 500,
                'message': f'摘要生成失败: {str(e)}'
            }
    
    @app.post("/api/translation/test-summary")
    async def test_summary_api(request: SummaryRequest, admin_user = Depends(admin_required)):
        """测试摘要生成功能（多语言）"""
        try:
            # 优先使用前端传来的临时配置，但需要合并已保存配置中的密钥
            if request.config:
                try:
                    # 先加载已保存的配置（用于获取密钥）
                    saved_config = translation_manager.load_config()
                    
                    # 合并配置：临时配置 + 已保存配置中的密钥
                    merged_config_dict = request.config.copy()
                    
                    # 如果临时配置中没有api_key，从已保存配置中获取
                    if saved_config:
                        # 合并 llm 配置中的 api_key
                        if merged_config_dict.get('llm') and not merged_config_dict['llm'].get('api_key'):
                            if saved_config.llm and saved_config.llm.get('api_key'):
                                merged_config_dict['llm']['api_key'] = saved_config.llm['api_key']
                        
                        # 合并 translation_llm 配置中的 api_key
                        if merged_config_dict.get('translation_llm') and not merged_config_dict['translation_llm'].get('api_key'):
                            if saved_config.translation_llm and saved_config.translation_llm.get('api_key'):
                                merged_config_dict['translation_llm']['api_key'] = saved_config.translation_llm['api_key']
                        
                        # 合并 baidu 配置中的 app_secret
                        if merged_config_dict.get('baidu') and not merged_config_dict['baidu'].get('app_secret'):
                            if saved_config.baidu and saved_config.baidu.get('app_secret'):
                                merged_config_dict['baidu']['app_secret'] = saved_config.baidu['app_secret']
                        
                        # 合并 custom 配置中的密钥
                        if merged_config_dict.get('custom'):
                            if not merged_config_dict['custom'].get('api_key') and saved_config.custom and saved_config.custom.get('api_key'):
                                merged_config_dict['custom']['api_key'] = saved_config.custom['api_key']
                            if not merged_config_dict['custom'].get('app_secret') and saved_config.custom and saved_config.custom.get('app_secret'):
                                merged_config_dict['custom']['app_secret'] = saved_config.custom['app_secret']
                        
                        # 合并 summary 配置中的 dedicated_llm api_key
                        if merged_config_dict.get('summary') and merged_config_dict['summary'].get('dedicated_llm'):
                            if not merged_config_dict['summary']['dedicated_llm'].get('api_key'):
                                if saved_config.summary and saved_config.summary.get('dedicated_llm') and saved_config.summary['dedicated_llm'].get('api_key'):
                                    merged_config_dict['summary']['dedicated_llm']['api_key'] = saved_config.summary['dedicated_llm']['api_key']
                    
                    config = TranslationConfig(**merged_config_dict)
                except Exception as e:
                    logger.error(f"解析前端配置失败: {e}")
                    return {
                        'code': 400,
                        'message': f'配置格式错误: {str(e)}'
                    }
            else:
                config = translation_manager.load_config()
                if not config:
                    return {
                        'code': 400,
                        'message': '翻译配置未设置，请先保存配置或在测试时提供临时配置'
                    }
            
            # 限制测试内容长度，避免消耗过多tokens
            if request.languages:
                test_languages = {}
                for lang_code, content in request.languages.items():
                    test_languages[lang_code] = content[:2000] if len(content) > 2000 else content
            else:
                test_content = request.content[:2000] if request.content and len(request.content) > 2000 else request.content
                source_lang = config.default_source_lang or 'zh'
                test_languages = {source_lang: test_content} if test_content else {}
            
            test_request = SummaryRequest(
                article_id=request.article_id,
                languages=test_languages,
                max_length=request.max_length,
                style=request.style
            )
            
            # 测试模式：AI失败不回退到TextRank，直接显示错误
            result = await translation_manager.generate_summary(config, test_request, is_test=True)
            
            # 计算原始内容和摘要的总长度
            original_total_length = sum(len(c) for c in (request.languages or {}).values()) if request.languages else (len(request.content) if request.content else 0)
            summary_total_length = sum(len(s) for s in (result.summaries or {}).values()) if result.summaries else 0
            
            return {
                'code': 200,
                'message': '测试完成',
                'data': {
                    'success': result.success,
                    'summaries': result.summaries,
                    'method': result.method,
                    'processing_time': result.processing_time,
                    'toon_tokens': result.toon_tokens,
                    'token_saved_percent': result.token_saved_percent,
                    'error_message': result.error_message,
                    'original_total_length': original_total_length,
                    'summary_total_length': summary_total_length
                }
            }
                
        except Exception as e:
            logger.error(f"摘要测试API失败: {e}")
            return {
                'code': 500,
                'message': f'摘要测试失败: {str(e)}'
            }
    
