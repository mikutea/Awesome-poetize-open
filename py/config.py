import os
import secrets
import httpx
import logging

logger = logging.getLogger(__name__)

# FastAPI应用密钥（用于中间件加密）
SECRET_KEY = os.environ.get('SECRET_KEY', secrets.token_hex(16))

# ================================ Redis配置 ================================
# Redis连接配置
REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))
REDIS_PASSWORD = os.environ.get('REDIS_PASSWORD', '123456')
REDIS_DB = int(os.environ.get('REDIS_DB', 0))
REDIS_MAX_CONNECTIONS = int(os.environ.get('REDIS_MAX_CONNECTIONS', 20))


# 自动检测后端地址
def detect_backend_url():
    # 1. 优先使用环境变量
    if 'JAVA_BACKEND_HOST' in os.environ:
        host = os.environ.get('JAVA_BACKEND_HOST')
        port = os.environ.get('JAVA_BACKEND_PORT', '8081')
        url = f"http://{host}:{port}"
        return url
    
    # 2. 使用默认地址
    default_url = "http://localhost:8081"
    return default_url

# 自动获取基础URL
BASE_BACKEND_URL = os.environ.get('JAVA_BACKEND_BASE_URL', detect_backend_url())

# API端点
JAVA_BACKEND_URL = os.environ.get('JAVA_BACKEND_URL', BASE_BACKEND_URL)

# 配置同步API
JAVA_CONFIG_URL = os.environ.get('JAVA_CONFIG_URL', f"{BASE_BACKEND_URL}")

# Java验证API
JAVA_AUTH_URL = os.environ.get('JAVA_AUTH_URL', f"{BASE_BACKEND_URL}/user/checkAdminAuth")

# 从Java后端获取前端URL的接口
JAVA_SITE_URL_API = f"{JAVA_BACKEND_URL}/webInfo/getSeoConfig/nginx"

# 从Java后端获取前端URL
async def get_frontend_url_from_java():
    """
    从Java后端获取配置的前端URL
    优先级：web_info.site_address > 环境变量SITE_URL > 默认值
    """
    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(JAVA_SITE_URL_API, timeout=5.0)
            if response.status_code == 200:
                data = response.json()
                site_address = data.get('site_address')
                if site_address and site_address != "http://localhost":
                    return site_address
    except Exception as e:
        logger.warning(f"从Java后端获取前端URL失败: {e}")
    
    return None

# 智能推断前端URL
async def detect_frontend_url():    
    # 1. 优先从Java后端获取配置的前端URL（web_info.site_address）
    java_site_url = await get_frontend_url_from_java()
    if java_site_url:
        return java_site_url
    
    # 2. 如果Java后端没有配置，检查环境变量SITE_URL
    site_url = os.environ.get('SITE_URL')
    if site_url:
        # 确保URL以http://或https://开头
        if not site_url.startswith("http://") and not site_url.startswith("https://"):
            site_url = "https://" + site_url
        # 移除末尾的斜杠
        if site_url.endswith("/"):
            site_url = site_url[:-1]
        return site_url
    
    # 3. 默认前端URL
    default_url = "http://localhost"
    return default_url

# 前端URL缓存
_frontend_url_cache = {
    'url': None,
    'timestamp': 0,
    'ttl': 300  # 缓存5分钟（300秒）
}

async def get_frontend_url(request=None):
    """
    获取前端URL，支持缓存机制
    优先级：
    1. 如果有request对象，从请求头中检测
    2. 如果缓存未过期，返回缓存值
    3. 否则实时调用Java接口获取最新配置
    """
    import time
    
    # 如果有request对象，尝试从请求头中检测
    if request is not None:
        url_from_request = _detect_frontend_url_from_request(request)
        if url_from_request:
            return url_from_request
    
    # 检查缓存是否有效
    current_time = time.time()
    if (_frontend_url_cache['url'] and 
        current_time - _frontend_url_cache['timestamp'] < _frontend_url_cache['ttl']):
        return _frontend_url_cache['url']
    
    # 缓存已过期或为空，重新获取
    url = await detect_frontend_url()
    
    # 更新缓存
    _frontend_url_cache['url'] = url
    _frontend_url_cache['timestamp'] = current_time
    
    return url

def _detect_frontend_url_from_request(request):
    """
    从HTTP请求头中智能检测前端URL
    这个函数在处理API请求时调用，能获取到实际访问的域名
    """
    try:
        # 获取请求头信息
        host = None
        scheme = 'http'  # 默认协议
        
        # 尝试多种方式获取主机名
        if hasattr(request, 'headers'):
            # FastAPI/Starlette请求对象
            headers = request.headers
            host = headers.get('host') or headers.get('x-forwarded-host') or headers.get('x-original-host')
            
            # 检测协议
            if headers.get('x-forwarded-proto') == 'https' or headers.get('x-forwarded-ssl') == 'on':
                scheme = 'https'
            elif hasattr(request, 'url') and request.url.scheme:
                scheme = request.url.scheme
        elif hasattr(request, 'environ'):
            # WSGI请求对象
            environ = request.environ
            host = environ.get('HTTP_HOST') or environ.get('HTTP_X_FORWARDED_HOST')
            if environ.get('HTTP_X_FORWARDED_PROTO') == 'https' or environ.get('HTTPS') == 'on':
                scheme = 'https'
        
        if host:
            # 处理端口号
            if ':' in host:
                hostname, port = host.split(':', 1)
                port = int(port)
                # 如果是标准端口，不需要包含在URL中
                if (scheme == 'http' and port == 80) or (scheme == 'https' and port == 443):
                    detected_url = f"{scheme}://{hostname}"
                else:
                    detected_url = f"{scheme}://{host}"
            else:
                detected_url = f"{scheme}://{host}"
            
            return detected_url
            
    except Exception as e:
        logger.warning(f"检测前端URL失败: {e}")
    
    # 如果检测失败，返回None
    return None

# 为了向后兼容，保留FRONTEND_URL变量，但标记为已弃用
# 注意：这个变量不会自动更新，建议使用get_frontend_url()函数
FRONTEND_URL = None

async def init_frontend_url():
    """
    初始化前端URL缓存
    这个函数现在主要用于预热缓存，不是必需的
    """
    url = await get_frontend_url()
    logger.info(f"前端URL缓存已初始化: {url}")
    return url

# Python服务端口
PYTHON_SERVICE_PORT = int(os.environ.get('PORT', 5000))

# 输出检测到的配置
logger.info(f"后端URL: {BASE_BACKEND_URL}")
logger.info(f"服务端口: {PYTHON_SERVICE_PORT}")