"""
翻译管理API服务
提供翻译配置管理、翻译服务接口
"""

import os
import json
import httpx
import asyncio
from typing import Dict, Any, Optional, List
from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel, Field
from auth_decorator import admin_required
import logging
from cryptography.fernet import Fernet
import time
# 导入OpenAI和Anthropic官方库
from openai import AsyncOpenAI
from anthropic import AsyncAnthropic
from config import JAVA_BACKEND_URL
logger = logging.getLogger(__name__)

# 翻译配置存储文件
TRANSLATION_CONFIG_FILE = os.path.join(os.path.dirname(__file__), 'data', 'translation_config.json')

# 加密工具类
class CryptoManager:
    """密钥加密管理器"""
    
    def __init__(self):
        self.key_file = os.path.join(os.path.dirname(__file__), 'data', 'translation_encryption.key')
        self.key = self._get_or_generate_key()
    
    def _get_or_generate_key(self):
        """获取或生成加密密钥"""
        if os.path.exists(self.key_file):
            with open(self.key_file, 'rb') as f:
                return f.read()
        else:
            # 确保data目录存在
            os.makedirs(os.path.dirname(self.key_file), exist_ok=True)
            key = Fernet.generate_key()
            with open(self.key_file, 'wb') as f:
                f.write(key)
            return key
    
    def encrypt(self, text: str) -> str:
        """加密文本"""
        if not text:
            return ""
        try:
            cipher = Fernet(self.key)
            encrypted_data = cipher.encrypt(text.encode())
            return encrypted_data.decode()
        except Exception as e:
            logger.error(f"加密失败: {e}")
            return text  # 如果加密失败，返回原文本
    
    def decrypt(self, encrypted_text: str) -> str:
        """解密文本"""
        if not encrypted_text:
            return ""
        try:
            cipher = Fernet(self.key)
            decrypted_data = cipher.decrypt(encrypted_text.encode())
            return decrypted_data.decode()
        except Exception as e:
            logger.error(f"解密失败: {e}")
            return encrypted_text  # 如果解密失败，返回原文本

# 创建加密管理器实例
crypto_manager = CryptoManager()

class TranslationConfig(BaseModel):
    """翻译配置模型"""
    type: str = Field(..., description="翻译类型: baidu|custom|llm")
    
    # 百度翻译配置
    baidu: Optional[dict] = Field(None, description="百度翻译配置")
    
    # 自定义API配置  
    custom: Optional[dict] = Field(None, description="自定义API配置")
    
    # 大模型配置
    llm: Optional[dict] = Field(None, description="大模型配置")
    
    # 摘要生成配置
    summary: Optional[dict] = Field(None, description="摘要生成配置")
    
    # 默认语言配置
    default_source_lang: Optional[str] = Field("zh", description="默认源语言")
    default_target_lang: Optional[str] = Field("en", description="默认目标语言")

class TranslationRequest(BaseModel):
    """翻译请求模型"""
    text: str = Field(..., description="待翻译文本")
    source_lang: Optional[str] = Field("auto", description="源语言代码")
    target_lang: Optional[str] = Field("en", description="目标语言代码")

class TestTranslationRequest(BaseModel):
    """测试翻译请求模型"""
    text: str = Field(..., description="待翻译文本")

class TranslationResponse(BaseModel):
    """翻译响应模型"""
    success: bool = Field(..., description="翻译是否成功")
    translated_text: Optional[str] = Field(None, description="翻译结果")
    source_lang: Optional[str] = Field(None, description="检测到的源语言")
    target_lang: Optional[str] = Field(None, description="目标语言")
    engine: Optional[str] = Field(None, description="使用的翻译引擎")
    error_message: Optional[str] = Field(None, description="错误信息")

class SummaryRequest(BaseModel):
    """摘要生成请求模型"""
    content: str = Field(..., description="待生成摘要的文章内容")
    max_length: Optional[int] = Field(150, description="摘要最大长度")
    style: Optional[str] = Field("concise", description="摘要风格：concise简洁|detailed详细|academic学术")

class SummaryResponse(BaseModel):
    """摘要生成响应模型"""
    success: bool = Field(..., description="摘要生成是否成功")
    summary: Optional[str] = Field(None, description="生成的摘要")
    method: Optional[str] = Field(None, description="使用的摘要方法：ai|textrank")
    processing_time: Optional[float] = Field(None, description="处理时间（秒）")
    error_message: Optional[str] = Field(None, description="错误信息")

class TranslationManager:
    """翻译管理器"""
    
    def __init__(self):
        self.config_file = TRANSLATION_CONFIG_FILE
        self.ensure_data_dir()
    
    def ensure_data_dir(self):
        """确保数据目录存在"""
        data_dir = os.path.dirname(self.config_file)
        if not os.path.exists(data_dir):
            os.makedirs(data_dir)
    
    def load_config(self) -> Optional[TranslationConfig]:
        """加载翻译配置"""
        try:
            if not os.path.exists(self.config_file):
                logger.warning("翻译配置文件不存在")
                return None
            
            with open(self.config_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            # 确保基本结构存在
            if data is None:
                data = {}
            
            # 解密敏感字段 - 百度翻译
            if data.get('baidu') and isinstance(data['baidu'], dict):
                if data['baidu'].get('app_secret'):
                    try:
                        data['baidu']['app_secret'] = crypto_manager.decrypt(data['baidu']['app_secret'])
                    except Exception as e:
                        logger.error(f"解密百度翻译密钥失败: {e}")
                        data['baidu']['app_secret'] = None
            
            # 解密敏感字段 - 自定义API
            if data.get('custom') and isinstance(data['custom'], dict):
                if data['custom'].get('api_key'):
                    try:
                        data['custom']['api_key'] = crypto_manager.decrypt(data['custom']['api_key'])
                    except Exception as e:
                        logger.error(f"解密自定义API密钥失败: {e}")
                        data['custom']['api_key'] = None
                
                if data['custom'].get('app_secret'):
                    try:
                        data['custom']['app_secret'] = crypto_manager.decrypt(data['custom']['app_secret'])
                    except Exception as e:
                        logger.error(f"解密自定义API第二密钥失败: {e}")
                        data['custom']['app_secret'] = None
            
            # 解密敏感字段 - LLM
            if data.get('llm') and isinstance(data['llm'], dict):
                if data['llm'].get('api_key'):
                    try:
                        data['llm']['api_key'] = crypto_manager.decrypt(data['llm']['api_key'])
                    except Exception as e:
                        logger.error(f"解密LLM API密钥失败: {e}")
                        data['llm']['api_key'] = None
            
            return TranslationConfig(**data)
        except Exception as e:
            logger.error(f"加载翻译配置失败: {e}")
            return None
    
    def save_config(self, config: TranslationConfig) -> bool:
        """保存翻译配置"""
        try:
            data = config.dict()
            
            # 如果配置文件已存在，先读取原配置用于保留未更新的密钥
            original_data = {}
            if os.path.exists(self.config_file):
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    original_data = json.load(f)
            
            # 处理百度翻译配置的加密
            if data.get('baidu'):
                if 'app_secret' in data['baidu'] and data['baidu']['app_secret']:
                    # 加密新的密钥
                    data['baidu']['app_secret'] = crypto_manager.encrypt(data['baidu']['app_secret'])
                elif original_data.get('baidu', {}).get('app_secret'):
                    # 保留原有的加密密钥
                    if 'baidu' not in data:
                        data['baidu'] = {}
                    data['baidu']['app_secret'] = original_data['baidu']['app_secret']
            
            # 处理自定义API配置的加密
            if data.get('custom'):
                if 'api_key' in data['custom'] and data['custom']['api_key']:
                    # 加密新的API密钥
                    data['custom']['api_key'] = crypto_manager.encrypt(data['custom']['api_key'])
                elif original_data.get('custom', {}).get('api_key'):
                    # 保留原有的加密密钥
                    if 'custom' not in data:
                        data['custom'] = {}
                    data['custom']['api_key'] = original_data['custom']['api_key']
                
                if 'app_secret' in data['custom'] and data['custom']['app_secret']:
                    # 加密新的第二密钥
                    data['custom']['app_secret'] = crypto_manager.encrypt(data['custom']['app_secret'])
                elif original_data.get('custom', {}).get('app_secret'):
                    # 保留原有的加密密钥
                    if 'custom' not in data:
                        data['custom'] = {}
                    data['custom']['app_secret'] = original_data['custom']['app_secret']
            
            # 处理LLM配置的加密
            if data.get('llm'):
                if 'api_key' in data['llm'] and data['llm']['api_key']:
                    # 加密新的API密钥
                    data['llm']['api_key'] = crypto_manager.encrypt(data['llm']['api_key'])
                elif original_data.get('llm', {}).get('api_key'):
                    # 保留原有的加密密钥
                    if 'llm' not in data:
                        data['llm'] = {}
                    data['llm']['api_key'] = original_data['llm']['api_key']
            
            with open(self.config_file, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            logger.info("翻译配置保存成功")
            return True
        except Exception as e:
            logger.error(f"保存翻译配置失败: {e}")
            return False
    
    async def translate_text(self, request: TranslationRequest) -> TranslationResponse:
        """执行翻译"""
        config = self.load_config()
        if not config:
            return TranslationResponse(
                success=False,
                error_message="翻译配置未设置"
            )
        
        try:
            if config.type == "baidu":
                return await self._baidu_translate(config, request)
            elif config.type == "custom":
                return await self._custom_api_translate(config, request)
            elif config.type == "llm":
                return await self._translate_with_llm(config, request)
            else:
                return TranslationResponse(
                    success=False,
                    error_message="不支持的翻译类型"
                )
        except Exception as e:
            logger.error(f"翻译失败: {e}")
            return TranslationResponse(
                success=False,
                error_message=f"翻译失败: {str(e)}"
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
            async with httpx.AsyncClient(timeout=30) as client:
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
            async with httpx.AsyncClient(timeout=30) as client:
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
        """使用大模型翻译"""
        llm_config = config.llm or {}
        api_url = llm_config.get('api_url')
        api_key = llm_config.get('api_key')
        model = llm_config.get('model')
        interface_type = llm_config.get('interface_type', 'auto')  # 新增接口类型字段
        
        if not api_url or not api_key:
            return TranslationResponse(
                success=False,
                error_message="大模型配置不完整"
            )
        
        # 语言映射和默认设置
        lang_map = {
            'zh': '中文',
            'en': '英文',
            'ja': '日文',
            'ko': '韩文',
            'fr': '法文',
            'de': '德文',
            'es': '西班牙文',
            'ru': '俄文',
            'auto': '自动检测语言'
        }
        
        # 使用配置中的默认语言设置，如果请求中没有指定的话
        default_source = getattr(config, 'default_source_lang', 'zh')
        default_target = getattr(config, 'default_target_lang', 'en')
        
        source_lang = request.source_lang if request.source_lang and request.source_lang != 'auto' else default_source
        target_lang = request.target_lang if request.target_lang else default_target
        
        # 转换为中文语言名称，用于提示词
        source_lang_name = lang_map.get(source_lang, source_lang)
        target_lang_name = lang_map.get(target_lang, target_lang)
        
        # 检测文本格式（简单判断是否包含Markdown语法）
        text_format = 'Markdown' if any(marker in request.text for marker in ['#', '*', '**', '[', ']', '```']) else '纯文本'
        
        # 构建翻译提示词，支持占位符替换
        default_prompt = f"请将以下{source_lang_name}文本翻译为{target_lang_name}，保持原意和格式，只返回翻译结果："
        prompt_template = llm_config.get('prompt') or default_prompt
        
        # 替换占位符
        prompt = prompt_template.replace('{source_lang}', source_lang_name) \
                              .replace('{target_lang}', target_lang_name) \
                              .replace('{format}', text_format)
        
        full_prompt = f"{prompt}\n\n{request.text}"
        
        # 根据接口类型或模型类型选择请求方式
        if interface_type == 'openai':
            # 强制使用OpenAI接口格式
            return await self._openai_translate(config, full_prompt)
        elif interface_type == 'anthropic':
            # 强制使用Anthropic接口格式
            return await self._anthropic_translate(config, full_prompt)
        elif interface_type == 'auto' or interface_type is None:
            # 自动检测（原有逻辑）
            if model and any(x in model.lower() for x in ['gpt', 'openai']):
                return await self._openai_translate(config, full_prompt)
            elif model and any(x in model.lower() for x in ['claude', 'anthropic']):
                return await self._anthropic_translate(config, full_prompt)
            else:
                return await self._custom_llm_translate(config, full_prompt)
        else:
            # 默认使用自定义LLM接口
            return await self._custom_llm_translate(config, full_prompt)
    
    async def _openai_translate(self, config: TranslationConfig, prompt: str) -> TranslationResponse:
        """OpenAI翻译"""
        try:
            llm_config = config.llm or {}
            api_key = llm_config.get('api_key')
            api_url = llm_config.get('api_url', 'https://api.openai.com/v1')
            model = llm_config.get('model', 'gpt-3.5-turbo')
            
            if not api_key:
                return TranslationResponse(
                    success=False,
                    error_message="OpenAI API密钥未配置"
                )
            
            client = AsyncOpenAI(
                api_key=api_key,
                base_url=api_url,
                timeout=30.0
            )
            
            response = await client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=2000
            )
            
            if response.choices:
                translated_text = response.choices[0].message.content.strip()
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
            logger.error(f"OpenAI翻译失败: {e}")
            return TranslationResponse(
                success=False,
                error_message=f"OpenAI翻译失败: {str(e)}"
            )
    
    async def _anthropic_translate(self, config: TranslationConfig, prompt: str) -> TranslationResponse:
        """Anthropic翻译"""
        try:
            llm_config = config.llm or {}
            api_key = llm_config.get('api_key')
            model = llm_config.get('model', 'claude-3-5-sonnet-20241022')
            
            if not api_key:
                return TranslationResponse(
                    success=False,
                    error_message="Anthropic API密钥未配置"
                )
            
            client = AsyncAnthropic(
                api_key=api_key,
                timeout=30.0
            )
            
            response = await client.messages.create(
                model=model,
                max_tokens=2000,
                messages=[
                    {"role": "user", "content": prompt}
                ]
            )
            
            if response.content:
                translated_text = response.content[0].text.strip()
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
                
        except Exception as e:
            logger.error(f"Anthropic翻译失败: {e}")
            return TranslationResponse(
                success=False,
                error_message=f"Anthropic翻译失败: {str(e)}"
            )
    
    async def _custom_llm_translate(self, config: TranslationConfig, prompt: str) -> TranslationResponse:
        """自定义大模型翻译"""
        llm_config = config.llm or {}
        api_url = llm_config.get('api_url')
        api_key = llm_config.get('api_key')
        model = llm_config.get('model', 'gpt-3.5-turbo')
        
        if not api_url:
            return TranslationResponse(
                success=False,
                error_message="自定义模型API地址未配置"
            )
        
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
            'max_tokens': 2000
        }
        
        try:
            async with httpx.AsyncClient(timeout=30) as client:
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
        """测试翻译服务"""
        start_time = time.time()
        
        config = self.load_config()
        if not config:
            return {
                'success': False,
                'error_message': '翻译配置未设置'
            }
        
        translation_request = TranslationRequest(
            text=request.text,
            source_lang=request.source_lang,
            target_lang=request.target_lang
        )
        
        result = await self.translate(config, translation_request)
        
        processing_time = time.time() - start_time
        
        return {
            'success': result.success,
            'translated_text': result.translated_text,
            'source_lang': result.source_lang,
            'target_lang': result.target_lang,
            'engine': result.engine,
            'error_message': result.error_message,
            'processing_time': round(processing_time, 2)
        }
    
    async def generate_summary(self, config: TranslationConfig, request: SummaryRequest) -> SummaryResponse:
        """生成文章摘要（智能摘要优先，回退到TextRank）"""
        import time
        start_time = time.time()
        
        # 检查是否启用了AI摘要（默认启用）
        summary_config = config.summary or {}
        ai_enabled = summary_config.get('ai_enabled', True)  # 默认为True，启用AI摘要
        
        if ai_enabled and config.type == 'llm' and config.llm:
            try:
                # 尝试使用AI生成摘要
                ai_summary = await self._generate_ai_summary(config, request)
                if ai_summary.success:
                    processing_time = time.time() - start_time
                    ai_summary.processing_time = round(processing_time, 2)
                    return ai_summary
                else:
                    logger.warn(f"AI摘要生成失败，回退到TextRank: {ai_summary.error_message}")
            except Exception as e:
                logger.error(f"AI摘要生成异常，回退到TextRank: {e}")
        
        # 回退到Java后端的TextRank算法
        try:
            textrank_summary = await self._generate_textrank_summary(request)
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
    
    async def _generate_ai_summary(self, config: TranslationConfig, request: SummaryRequest) -> SummaryResponse:
        """使用AI生成摘要"""
        llm_config = config.llm or {}
        summary_config = config.summary or {}
        
        # 构建摘要提示词
        style_prompts = {
            'concise': '生成一个简洁明了的摘要，突出文章的核心观点',
            'detailed': '生成一个详细的摘要，包含文章的主要内容和关键信息',
            'academic': '生成一个学术风格的摘要，使用专业术语和结构化表达'
        }
        
        style_desc = style_prompts.get(request.style, style_prompts['concise'])
        default_prompt = f"请为以下文章{style_desc}，摘要长度控制在{request.max_length}字符以内。请直接返回摘要内容，不要添加任何前缀或说明："
        
        prompt_template = summary_config.get('prompt') or default_prompt
        full_prompt = f"{prompt_template}\n\n{request.content}"
        
        # 使用与翻译相同的LLM接口
        interface_type = llm_config.get('interface_type', 'auto')
        model = llm_config.get('model', '')
        
        if interface_type == 'openai':
            return await self._openai_summary(config, full_prompt)
        elif interface_type == 'anthropic':
            return await self._anthropic_summary(config, full_prompt)
        elif interface_type == 'auto' or interface_type is None:
            if model and any(x in model.lower() for x in ['gpt', 'openai']):
                return await self._openai_summary(config, full_prompt)
            elif model and any(x in model.lower() for x in ['claude', 'anthropic']):
                return await self._anthropic_summary(config, full_prompt)
            else:
                return await self._custom_llm_summary(config, full_prompt)
        else:
            return await self._custom_llm_summary(config, full_prompt)
    
    async def _openai_summary(self, config: TranslationConfig, prompt: str) -> SummaryResponse:
        """OpenAI摘要生成"""
        try:
            llm_config = config.llm or {}
            api_key = llm_config.get('api_key')
            api_url = llm_config.get('api_url', 'https://api.openai.com/v1')
            model = llm_config.get('model', 'gpt-3.5-turbo')
            
            if not api_key:
                return SummaryResponse(
                    success=False,
                    error_message="OpenAI API密钥未配置"
                )
            
            client = AsyncOpenAI(
                api_key=api_key,
                base_url=api_url,
                timeout=30.0
            )
            
            response = await client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=500  # 摘要不需要太多tokens
            )
            
            if response.choices:
                summary = response.choices[0].message.content.strip()
                return SummaryResponse(
                    success=True,
                    summary=summary,
                    method="ai-openai"
                )
            else:
                return SummaryResponse(
                    success=False,
                    error_message="OpenAI返回空响应"
                )
                
        except Exception as e:
            logger.error(f"OpenAI摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"OpenAI摘要生成失败: {str(e)}"
            )
    
    async def _anthropic_summary(self, config: TranslationConfig, prompt: str) -> SummaryResponse:
        """Anthropic摘要生成"""
        try:
            llm_config = config.llm or {}
            api_key = llm_config.get('api_key')
            model = llm_config.get('model', 'claude-3-5-sonnet-20241022')
            
            if not api_key:
                return SummaryResponse(
                    success=False,
                    error_message="Anthropic API密钥未配置"
                )
            
            client = AsyncAnthropic(
                api_key=api_key,
                timeout=30.0
            )
            
            response = await client.messages.create(
                model=model,
                max_tokens=500,  # 摘要不需要太多tokens
                messages=[
                    {"role": "user", "content": prompt}
                ]
            )
            
            if response.content:
                summary = response.content[0].text.strip()
                return SummaryResponse(
                    success=True,
                    summary=summary,
                    method="ai-anthropic"
                )
            else:
                return SummaryResponse(
                    success=False,
                    error_message="Anthropic返回空响应"
                )
                
        except Exception as e:
            logger.error(f"Anthropic摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"Anthropic摘要生成失败: {str(e)}"
            )
    
    async def _custom_llm_summary(self, config: TranslationConfig, prompt: str) -> SummaryResponse:
        """自定义大模型摘要生成"""
        llm_config = config.llm or {}
        api_url = llm_config.get('api_url')
        api_key = llm_config.get('api_key')
        model = llm_config.get('model', 'gpt-3.5-turbo')
        
        if not api_url:
            return SummaryResponse(
                success=False,
                error_message="自定义模型API地址未配置"
            )
        
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
            'max_tokens': 500
        }
        
        try:
            async with httpx.AsyncClient(timeout=30) as client:
                response = await client.post(api_url, json=payload, headers=headers)
                response.raise_for_status()
                
                data = response.json()
                if 'choices' in data and data['choices']:
                    summary = data['choices'][0]['message']['content'].strip()
                    return SummaryResponse(
                        success=True,
                        summary=summary,
                        method="ai-custom"
                    )
                else:
                    return SummaryResponse(
                        success=False,
                        error_message="自定义模型返回格式错误"
                    )
                    
        except Exception as e:
            logger.error(f"自定义LLM摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"自定义LLM摘要生成失败: {str(e)}"
            )
    
    async def _generate_textrank_summary(self, request: SummaryRequest) -> SummaryResponse:
        """调用Java后端的TextRank摘要生成"""
        try:
            
            
            payload = {
                'content': request.content,
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
                    return SummaryResponse(
                        success=True,
                        summary=data['data'],
                        method="textrank"
                    )
                else:
                    return SummaryResponse(
                        success=False,
                        error_message=data.get('message', 'TextRank摘要生成失败')
                    )
                    
        except Exception as e:
            logger.error(f"TextRank摘要生成失败: {e}")
            return SummaryResponse(
                success=False,
                error_message=f"TextRank摘要生成失败: {str(e)}"
            )

# 创建翻译管理器实例
translation_manager = TranslationManager()

def register_translation_api(app):
    """注册翻译API路由"""
    
    @app.get("/api/translation/config")
    async def get_translation_config(admin_user = Depends(admin_required)):
        """获取翻译配置"""
        try:
            config = translation_manager.load_config()
            if config:
                # 脱敏处理，不返回密钥信息
                safe_config = config.dict()
                
                # 处理百度翻译密钥显示
                if safe_config.get('baidu') and isinstance(safe_config['baidu'], dict):
                    if safe_config['baidu'].get('app_secret'):
                        safe_config['baidu']['app_secret'] = '***已配置***'
                
                # 处理自定义API密钥显示
                if safe_config.get('custom') and isinstance(safe_config['custom'], dict):
                    if safe_config['custom'].get('api_key'):
                        safe_config['custom']['api_key'] = '***已配置***'
                    if safe_config['custom'].get('app_secret'):
                        safe_config['custom']['app_secret'] = '***已配置***'
                
                # 处理LLM API密钥显示
                if safe_config.get('llm') and isinstance(safe_config['llm'], dict):
                    if safe_config['llm'].get('api_key'):
                        safe_config['llm']['api_key'] = '***已配置***'
                
                return {
                    'code': 200,
                    'message': '获取成功',
                    'data': safe_config
                }
            else:
                # 返回默认配置结构
                return {
                    'code': 200,
                    'message': '获取成功',
                    'data': {
                        'type': 'baidu',
                        'baidu': {'app_id': '', 'app_secret': ''},
                        'custom': {'api_url': '', 'api_key': '', 'app_secret': ''},
                        'llm': {'model': '', 'api_url': '', 'api_key': '', 'prompt': '', 'interface_type': 'auto'},
                        'summary': {'ai_enabled': True, 'style': 'concise', 'max_length': 150, 'prompt': '请为以下文章生成一个简洁明了的摘要，突出文章的核心观点和主要内容，摘要长度控制在{max_length}字符以内。请直接返回摘要内容，不要添加任何前缀或说明：'},
                        'default_source_lang': 'zh',
                        'default_target_lang': 'en'
                    }
                }
        except Exception as e:
            logger.error(f"获取翻译配置失败: {e}")
            return {
                'code': 500,
                'message': f'获取配置失败: {str(e)}'
            }
    
    @app.post("/api/translation/config")
    async def save_translation_config(config: TranslationConfig, admin_user = Depends(admin_required)):
        """保存翻译配置"""
        try:
            success = translation_manager.save_config(config)
            if success:
                return {
                    'code': 200,
                    'message': '配置保存成功'
                }
            else:
                return {
                    'code': 500,
                    'message': '配置保存失败'
                }
        except Exception as e:
            logger.error(f"保存翻译配置失败: {e}")
            return {
                'code': 500,
                'message': f'保存配置失败: {str(e)}'
            }
    
    @app.post("/api/translation/test")
    async def test_translation(request: TestTranslationRequest, admin_user = Depends(admin_required)):
        """测试翻译"""
        try:
            # 加载配置获取默认语言设置
            config = translation_manager.load_config()
            if not config:
                return {
                    'code': 500,
                    'message': '翻译配置未设置'
                }
            
            # 使用配置中的默认语言创建翻译请求
            translation_request = TranslationRequest(
                text=request.text,
                source_lang=getattr(config, 'default_source_lang', 'zh'),
                target_lang=getattr(config, 'default_target_lang', 'en')
            )
            
            result = await translation_manager.translate_text(translation_request)
            if result.success:
                return {
                    'code': 200,
                    'message': '翻译成功',
                    'data': {
                        'translated_text': result.translated_text,
                        'engine': result.engine,
                        'source_lang': translation_request.source_lang,
                        'target_lang': translation_request.target_lang
                    }
                }
            else:
                return {
                    'code': 400,
                    'message': result.error_message
                }
        except Exception as e:
            logger.error(f"测试翻译失败: {e}")
            return {
                'code': 500,
                'message': f'翻译测试失败: {str(e)}'
            }
    
    @app.post("/api/translation/translate")
    async def translate_text_api(request: TranslationRequest):
        """翻译文本API（对外提供）"""
        try:
            result = await translation_manager.translate_text(request)
            if result.success:
                return {
                    'code': 200,
                    'message': '翻译成功',
                    'data': {
                        'translated_text': result.translated_text,
                        'engine': result.engine
                    }
                }
            else:
                return {
                    'code': 400,
                    'message': result.error_message
                }
        except Exception as e:
            logger.error(f"翻译失败: {e}")
            return {
                'code': 500,
                'message': f'翻译失败: {str(e)}'
            }
    
    @app.post("/api/translation/generate-summary")
    async def generate_summary_api(request: SummaryRequest, admin_user = Depends(admin_required)):
        """生成文章摘要"""
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
                        'summary': result.summary,
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
        """测试摘要生成功能"""
        try:
            config = translation_manager.load_config()
            if not config:
                return {
                    'code': 400,
                    'message': '翻译配置未设置'
                }
            
            # 限制测试内容长度，避免消耗过多tokens
            test_content = request.content[:2000] if len(request.content) > 2000 else request.content
            test_request = SummaryRequest(
                content=test_content,
                max_length=request.max_length,
                style=request.style
            )
            
            result = await translation_manager.generate_summary(config, test_request)
            
            return {
                'code': 200,
                'message': '测试完成',
                'data': {
                    'success': result.success,
                    'summary': result.summary,
                    'method': result.method,
                    'processing_time': result.processing_time,
                    'error_message': result.error_message,
                    'original_length': len(request.content),
                    'summary_length': len(result.summary) if result.summary else 0
                }
            }
                
        except Exception as e:
            logger.error(f"摘要测试API失败: {e}")
            return {
                'code': 500,
                'message': f'摘要测试失败: {str(e)}'
            }
    
    logger.info("翻译管理API已注册") 