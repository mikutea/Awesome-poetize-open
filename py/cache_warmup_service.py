#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
缓存预热服务
在服务启动时预加载关键缓存数据，提升首次访问性能
"""

import asyncio
import logging
from cache_service import get_cache_service
from cache_constants import CacheConstants

logger = logging.getLogger(__name__)


class CacheWarmupService:
    """缓存预热服务"""

    def __init__(self):
        self.cache_service = get_cache_service()
    
    async def warmup_all_caches(self) -> bool:
        """预热所有缓存"""
        logger.info("开始缓存预热...")

        success_count = 0
        total_tasks = 8

        # 预热任务列表
        tasks = [
            self._warmup_seo_config,
            self._warmup_web_info,
            self._warmup_ai_chat_config,
            self._warmup_captcha_config,
            self._warmup_email_config,
            self._warmup_translation_config,
            self._warmup_seo_content,
            self._warmup_basic_auth_data
        ]

        # 并发执行预热任务
        try:
            results = await asyncio.gather(*[task() for task in tasks], return_exceptions=True)

            for i, result in enumerate(results):
                if result is True:
                    success_count += 1
                elif isinstance(result, Exception):
                    logger.error(f"预热任务异常: {tasks[i].__name__} - {result}")

            success_rate = (success_count / total_tasks) * 100
            logger.info(f"缓存预热完成: 成功 {success_count}/{total_tasks} 个任务 ({success_rate:.1f}%)")

            return success_count > 0  # 至少有一个任务成功

        except Exception as e:
            logger.error(f"缓存预热失败: {e}")
            return False
    
    async def _warmup_seo_config(self) -> bool:
        """预热SEO配置"""
        try:
            from seo_api import get_seo_config
            seo_config = await get_seo_config()
            if seo_config:
                success = self.cache_service.set_persistent_cache(
                    CacheConstants.SEO_CONFIG_KEY,
                    seo_config
                )
                if success:
                    logger.info("SEO配置预热成功")
                return success
            else:
                logger.warning("SEO配置为空")
                return False
        except Exception as e:
            logger.error(f"SEO配置预热失败: {e}")
            return False
    
    async def _warmup_web_info(self) -> bool:
        """预热网站信息"""
        try:
            from web_admin_api import get_web_info
            web_info = await get_web_info()
            if web_info:
                success1 = self.cache_service.set_persistent_cache(
                    CacheConstants.WEB_INFO_KEY,
                    web_info
                )
                success2 = self.cache_service.set_persistent_cache(
                    CacheConstants.WEB_INFO_ADMIN_KEY,
                    web_info
                )
                success = success1 and success2
                if success:
                    logger.info("网站信息预热成功")
                return success
            else:
                logger.warning("网站信息为空")
                return False
        except Exception as e:
            logger.error(f"网站信息预热失败: {e}")
            return False
    
    async def _warmup_ai_chat_config(self) -> bool:
        """预热AI聊天配置"""
        try:
            from ai_chat_api import get_ai_chat_config
            decrypted_config, display_config = get_ai_chat_config()
            if decrypted_config and display_config:
                cache_data = {
                    'decrypted': decrypted_config,
                    'display': display_config
                }
                success = self.cache_service.set_persistent_cache(
                    CacheConstants.AI_CHAT_CONFIG_KEY,
                    cache_data
                )
                if success:
                    logger.info("AI聊天配置预热成功")
                return success
            else:
                logger.warning("AI聊天配置为空")
                return False
        except Exception as e:
            logger.error(f"AI聊天配置预热失败: {e}")
            return False
    
    async def _warmup_captcha_config(self) -> bool:
        """预热验证码配置"""
        try:
            from captcha_api import get_captcha_config
            captcha_config = get_captcha_config()
            if captcha_config:
                success1 = self.cache_service.set_persistent_cache(
                    CacheConstants.CAPTCHA_CONFIG_KEY,
                    captcha_config
                )
                public_config = {
                    "enable": captcha_config.get('enable', False),
                    "screenSizeThreshold": captcha_config.get('screenSizeThreshold', 768),
                    "forceSlideForMobile": captcha_config.get('forceSlideForMobile', True),
                    "slide": captcha_config.get('slide', {}),
                    "checkbox": captcha_config.get('checkbox', {})
                }
                success2 = self.cache_service.set_persistent_cache(
                    CacheConstants.CAPTCHA_PUBLIC_CONFIG_KEY,
                    public_config
                )
                success = success1 and success2
                if success:
                    logger.info("验证码配置预热成功")
                return success
            else:
                logger.warning("验证码配置为空")
                return False
        except Exception as e:
            logger.error(f"验证码配置预热失败: {e}")
            return False
    
    async def _warmup_email_config(self) -> bool:
        """预热邮件配置"""
        try:
            from email_api import get_email_configs
            email_configs = get_email_configs()
            if email_configs is not None:  # 允许空列表
                email_config_data = {
                    "configs": email_configs,
                    "defaultIndex": 0 if email_configs else -1
                }
                success = self.cache_service.set_persistent_cache(
                    CacheConstants.EMAIL_CONFIG_KEY,
                    email_config_data
                )
                if success:
                    logger.info("邮件配置预热成功")
                return success
            else:
                logger.warning("邮件配置获取失败")
                return False
        except Exception as e:
            logger.error(f"邮件配置预热失败: {e}")
            return False

    async def _warmup_translation_config(self) -> bool:
        """预热翻译配置"""
        try:
            from translation_api import translation_manager

            success_count = 0

            # 预热完整翻译配置
            try:
                full_config = translation_manager.load_config()
                if full_config:
                    logger.info("翻译完整配置预热成功")
                    success_count += 1
                else:
                    logger.warning("翻译完整配置为空")
            except Exception as e:
                logger.warning(f"翻译完整配置预热失败: {e}")

            # 预热默认语言配置
            try:
                default_lang_config = translation_manager.get_default_languages()
                if default_lang_config:
                    logger.info("翻译默认语言配置预热成功")
                    success_count += 1
                else:
                    logger.warning("翻译默认语言配置为空")
            except Exception as e:
                logger.warning(f"翻译默认语言配置预热失败: {e}")

            # 至少有一个配置预热成功就认为成功
            return success_count > 0

        except Exception as e:
            logger.error(f"翻译配置预热失败: {e}")
            return False
    
    async def _warmup_seo_content(self) -> bool:
        """预热SEO内容（sitemap、robots.txt）"""
        try:
            from seo_api import generate_sitemap, generate_robots_txt
            success_count = 0

            # 生成并缓存sitemap
            try:
                sitemap_content = await generate_sitemap()
                if sitemap_content:
                    sitemap_success = self.cache_service.set(
                        CacheConstants.SEO_SITEMAP_KEY,
                        sitemap_content,
                        CacheConstants.SEO_SITEMAP_EXPIRE_TIME
                    )
                    if sitemap_success:
                        success_count += 1
                        logger.info("sitemap预热成功")
            except Exception as e:
                logger.warning(f"sitemap预热失败: {e}")

            # 生成并缓存robots.txt
            try:
                robots_content = await generate_robots_txt()
                if robots_content:
                    robots_success = self.cache_service.set(
                        CacheConstants.SEO_ROBOTS_KEY,
                        robots_content,
                        CacheConstants.SEO_ROBOTS_EXPIRE_TIME
                    )
                    if robots_success:
                        success_count += 1
                        logger.info("robots.txt预热成功")
            except Exception as e:
                logger.warning(f"robots.txt预热失败: {e}")

            return success_count > 0
        except Exception as e:
            logger.error(f"SEO内容预热失败: {e}")
            return False
    
    async def _warmup_basic_auth_data(self) -> bool:
        """预热基础认证数据"""
        try:
            auth_data = {
                "service_name": "poetize-python",
                "version": "1.0.0",
                "features": ["seo", "ai_chat", "email", "captcha"]
            }
            success = self.cache_service.set_persistent_cache(
                "poetize:auth:basic_info",
                auth_data
            )
            if success:
                logger.info("基础认证数据预热成功")
            return success
        except Exception as e:
            logger.error(f"基础认证数据预热失败: {e}")
            return False
    


# 全局缓存预热服务实例
_cache_warmup_service = None

def get_cache_warmup_service() -> CacheWarmupService:
    """获取缓存预热服务实例"""
    global _cache_warmup_service
    if _cache_warmup_service is None:
        _cache_warmup_service = CacheWarmupService()
    return _cache_warmup_service
