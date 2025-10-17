"""
POETIZE博客系统 - FastAPI后端服务
支持SEO优化、第三方登录、邮件发送等功能

主要功能模块：
- SEO优化和搜索引擎推送
- 第三方OAuth登录 (GitHub, Google, Twitter, Yandex, Gitee)
- 邮件配置管理和发送服务
- 智能验证码服务
- 多语言翻译
- AI聊天配置
"""

import os
import logging
import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.responses import RedirectResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from starlette.middleware.sessions import SessionMiddleware
from config import SECRET_KEY, JAVA_BACKEND_URL, FRONTEND_URL, JAVA_CONFIG_URL, PYTHON_SERVICE_PORT
from py_three_login import oauth_login, oauth_callback
from redis_oauth_state_manager import oauth_state_manager
from json_config_cache import get_json_config_cache

from ai_chat_api import register_ai_chat_api  # 处理AI聊天配置功能
from translation_api import register_translation_api  # 处理翻译管理功能
from auth_decorator import admin_required  # 导入管理员权限验证装饰器

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 定义应用生命周期管理
@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # ========== 启动时执行 ==========
    logger.info("应用启动中，开始初始化...")
    
    # JSON配置缓存预热
    try:
        logger.info("开始JSON配置缓存预热...")
        json_cache = get_json_config_cache()
        
        # 预热所有已知配置
        preload_configs = ['ai_chat_config', 'ai_api_config', 'translation_config']
        for config_name in preload_configs:
            try:
                json_cache.get_json_config(config_name)
                logger.debug(f"预热配置: {config_name}")
            except Exception as e:
                logger.warning(f"预热配置 {config_name} 失败: {e}")
        
        logger.info("JSON配置缓存预热完成")
    except Exception as e:
        logger.error(f"JSON配置缓存预热过程中发生异常: {e}")
        logger.info("应用将继续启动，但某些功能可能在首次访问时较慢")
    
    logger.info("应用启动完成")
    
    yield  # 应用运行中
    
    # ========== 关闭时执行 ==========
    logger.info("应用正在关闭...")
    logger.info("应用已关闭")

# 创建FastAPI应用实例
# 安全配置：禁用自动文档功能，避免在生产环境暴露API结构
app = FastAPI(
    title="POETIZE博客系统API",
    description="基于FastAPI的博客系统后端服务，提供SEO优化、第三方登录等功能",
    version="2.0.0",
    docs_url=None,        # 禁用Swagger UI文档 (/docs)
    redoc_url=None,       # 禁用ReDoc文档 (/redoc)
    openapi_url=None,     # 禁用OpenAPI schema (/openapi.json)
    lifespan=lifespan     # 使用新的生命周期管理
)

# 添加Session中间件（必须在其他中间件之前添加）
app.add_middleware(
    SessionMiddleware,
    secret_key=SECRET_KEY,
    max_age=3600,  # 1小时过期
    same_site='lax',  # 允许跨站点请求携带cookie
    https_only=False  # 开发环境允许HTTP
)

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
    max_age=3600
)

# 添加GZip压缩中间件，压缩响应数据以节省带宽
app.add_middleware(GZipMiddleware, minimum_size=1000)

@app.get("/")
async def index():
    return RedirectResponse(url=FRONTEND_URL)

# 健康检查接口
@app.get("/api/health")
async def health_check():
    """API健康检查端点，返回系统状态和版本信息"""
    return {
        'status': 'ok',
        'api_version': os.environ.get('VERSION', 'dev'),
        'app_version': os.environ.get('VERSION', 'dev'),
        'translation_model': 'integrated',
    }

# 健康检查接口
@app.get("/health")
async def health_check_old():
    """兼容旧的健康检查路径"""
    return await health_check()

# OAuth状态管理器调试端点
@app.get("/debug/oauth-states")
async def debug_oauth_states():
    """获取OAuth状态管理器的统计信息"""
    return oauth_state_manager.get_stats()



def register_all_apis(app):
    """注册所有API模块"""
    # 注册OAuth路由 - 支持多种路径格式
    app.add_api_route('/oauth/login/{provider}', oauth_login, methods=['GET'])
    app.add_api_route('/oauth/callback/{provider}', oauth_callback, methods=['GET'])

    # 兼容路由 - 支持前端直接调用的路径
    app.add_api_route('/login/{provider}', oauth_login, methods=['GET'])
    app.add_api_route('/callback/{provider}', oauth_callback, methods=['GET'])

    # 注册各个API模块
    register_ai_chat_api(app)  # 注册AI聊天配置API
    register_translation_api(app)  # 注册翻译管理API

    logger.info("所有API模块已注册完成")

# 注册所有API
register_all_apis(app)

if __name__ == '__main__':
    import uvicorn
    
    port = int(os.environ.get('PORT', PYTHON_SERVICE_PORT))
    debug = os.environ.get('DEBUG', 'False').lower() == 'true'
    
    logger.info("==========================================")
    logger.info("POETIZE博客系统 - FastAPI后端服务启动中...")
    logger.info("==========================================")
    logger.info(f"服务端口: {port}")
    logger.info(f"调试模式: {debug}")
    logger.info(f"Java后端: {JAVA_BACKEND_URL}")
    logger.info(f"前端地址: {FRONTEND_URL}")
    logger.info("==========================================")
    
    uvicorn.run(
        "main:app", 
        host='0.0.0.0', 
        port=port, 
        reload=debug,
        log_level="info",
        access_log=False  # 禁用访问日志，不再显示每个请求
    ) 