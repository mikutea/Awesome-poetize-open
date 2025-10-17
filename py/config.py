import os
import secrets
import socket
import httpx
from urllib.parse import urlparse
import json

# FastAPI应用密钥（用于中间件加密）
SECRET_KEY = os.environ.get('SECRET_KEY', secrets.token_hex(16))

# ================================ Redis配置 ================================
# Redis连接配置
REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))
REDIS_PASSWORD = os.environ.get('REDIS_PASSWORD', '123456')
REDIS_DB = int(os.environ.get('REDIS_DB', 0))
REDIS_MAX_CONNECTIONS = int(os.environ.get('REDIS_MAX_CONNECTIONS', 20))

print(f"Redis配置: {REDIS_HOST}:{REDIS_PORT}/{REDIS_DB}")

# 自动检测后端地址
def detect_backend_url():
    # 1. 优先使用环境变量
    if 'JAVA_BACKEND_HOST' in os.environ:
        host = os.environ.get('JAVA_BACKEND_HOST')
        port = os.environ.get('JAVA_BACKEND_PORT', '8081')
        url = f"http://{host}:{port}"
        print(f"从环境变量检测到Java后端: {url}")
        return url
    
    # 2. 使用默认地址
    default_url = "http://localhost:8081"
    print(f"无法检测到Java后端，使用默认地址: {default_url}")
    return default_url

# 自动获取基础URL
BASE_BACKEND_URL = os.environ.get('JAVA_BACKEND_BASE_URL', detect_backend_url())

# API端点
JAVA_BACKEND_URL = os.environ.get('JAVA_BACKEND_URL', BASE_BACKEND_URL)

# 配置同步API
JAVA_CONFIG_URL = os.environ.get('JAVA_CONFIG_URL', f"{BASE_BACKEND_URL}")

# Java验证API
JAVA_AUTH_URL = os.environ.get('JAVA_AUTH_URL', f"{BASE_BACKEND_URL}/user/checkAdminAuth")

# 智能推断前端URL
def detect_frontend_url():    
    # 1. 优先使用环境变量
    if 'FRONTEND_HOST' in os.environ:
        host = os.environ.get('FRONTEND_HOST')
        protocol = os.environ.get('FRONTEND_PROTOCOL', 'http')  # 支持HTTPS
        
        # 根据协议设置正确的默认端口
        if protocol == 'https':
            default_port = '443'
        else:
            default_port = '80'
        
        port = os.environ.get('FRONTEND_PORT', default_port)
        url = f"{protocol}://{host}"
        # 只有当端口不是标准端口(80/443)时才添加
        if (protocol == 'http' and port != '80') or (protocol == 'https' and port != '443'):
            url += f":{port}"
        print(f"从环境变量获取前端URL: {url}")
        return url
    
    # 2. 从后端URL推断（假设前端和后端在同一域名不同端口或路径）
    if BASE_BACKEND_URL != "http://localhost:8081":
        parsed = urlparse(BASE_BACKEND_URL)
        host = parsed.netloc.split(':')[0]
        # 如果不是本地地址，假设前端使用标准HTTP端口
        if host not in ('localhost', '127.0.0.1'):
            print(f"从后端URL推断前端URL: http://{host}")
            return f"http://{host}"
    
    # 3. 默认前端URL
    default_url = "http://localhost"
    print(f"无法检测到前端URL，使用默认值: {default_url}")
    return default_url

# 从HTTP请求中检测前端URL
def detect_frontend_url_from_request(request=None):
    """
    从HTTP请求头中智能检测前端URL
    这个函数在处理API请求时调用，能获取到实际访问的域名
    """
    if request is None:
        # 如果没有请求对象，使用静态检测
        return FRONTEND_URL
    
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
            
            print(f"从请求头检测到前端URL: {detected_url}")
            return detected_url
            
    except Exception as e:
        print(f"从请求头检测前端URL失败: {str(e)}")
    
    # 如果检测失败，返回默认值
    return FRONTEND_URL

# 前端URL
FRONTEND_URL = os.environ.get('FRONTEND_URL', detect_frontend_url())

# Python服务端口
PYTHON_SERVICE_PORT = int(os.environ.get('PORT', 5000))

# 输出检测到的配置
print(f"检测到Java后端URL: {BASE_BACKEND_URL}")
print(f"第三方登录API: {JAVA_BACKEND_URL}")
print(f"配置获取API: {JAVA_CONFIG_URL}")
print(f"验证API: {JAVA_AUTH_URL}")
print(f"前端URL: {FRONTEND_URL}")
print(f"Python服务端口: {PYTHON_SERVICE_PORT}")