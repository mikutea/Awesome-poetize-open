"""
权限验证装饰器
提供基于IP和Token的权限验证功能

主要功能：
- 基于IP的限流
- 基于Token的权限验证
- 支持内部网络和外部网络的访问控制
- 支持备用IP验证机制
"""
import functools
import httpx
import logging
import time
import os
import ipaddress
from fastapi import Request, HTTPException, Depends, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from typing import Optional
from config import JAVA_AUTH_URL

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('auth_decorator')

# 创建限流器 - 单IP最大请求数/时间窗口
class RateLimiter:
    def __init__(self, max_requests=10, window_seconds=60):
        self.max_requests = max_requests  # 每个窗口允许的最大请求数
        self.window_seconds = window_seconds  # 窗口时间（秒）
        self.request_logs = {}  # 存储请求记录 {ip: [(timestamp1), (timestamp2), ...]}
        
    def is_allowed(self, ip):
        """检查IP是否被允许访问"""
        current_time = time.time()
        
        # 清理过期记录
        if ip in self.request_logs:
            self.request_logs[ip] = [t for t in self.request_logs[ip] if current_time - t < self.window_seconds]
        else:
            self.request_logs[ip] = []
            
        # 检查是否超出限制
        if len(self.request_logs[ip]) >= self.max_requests:
            logger.warning(f"IP {ip} 请求过于频繁，被限流")
            return False
            
        # 记录本次请求
        self.request_logs[ip].append(current_time)
        return True

# 初始化限流器
admin_rate_limiter = RateLimiter(max_requests=20, window_seconds=60)

# Docker内部网络配置
DOCKER_INTERNAL_NETWORKS = [
    '127.0.0.0/8',      # 本地回环
]

# 从环境变量或配置文件动态获取当前Docker网络配置
def get_current_docker_network():
    """
    获取当前Docker网络配置，支持动态网段
    """
    # 尝试从环境变量获取
    docker_subnet = os.environ.get('DOCKER_SUBNET', '172.28.147.0/28')
    
    return docker_subnet

# 检查IP是否在内部网络范围内
def is_internal_network_ip(client_ip):
    """
    检查客户端IP是否在Docker内部网络范围内
    """
    try:
        client_addr = ipaddress.ip_address(client_ip)
        
        # 获取当前Docker网络配置
        current_subnet = get_current_docker_network()
        
        # 检查是否在当前Docker网段内
        try:
            current_network = ipaddress.ip_network(current_subnet, strict=False)
            if client_addr in current_network:
                return True
        except Exception as e:
            logger.warning(f"解析当前Docker网段失败: {e}")
        
        # 检查是否在预定义的内部网络范围内
        for network_str in DOCKER_INTERNAL_NETWORKS:
            try:
                network = ipaddress.ip_network(network_str, strict=False)
                if client_addr in network:
                    return True
            except Exception as e:
                logger.warning(f"解析网段 {network_str} 失败: {e}")
        
        return False
        
    except Exception as e:
        logger.warning(f"解析IP地址 {client_ip} 失败: {e}")
        return False

# 从环境变量获取内部服务IP列表（作为辅助验证）
def get_trusted_internal_ips():
    # 默认的受信任IP
    trusted_ips = [
        'localhost',
        '127.0.0.1'
    ]
    
    # 从环境变量添加JAVA_SERVICE_IP
    java_service_ip = os.environ.get('JAVA_SERVICE_IP', 'poetize-java')
    if java_service_ip:
        trusted_ips.append(java_service_ip)
    
    # 添加预渲染服务IP
    prerender_service_ip = os.environ.get('PRERENDER_SERVICE_IP', 'poetize-prerender')
    if prerender_service_ip:
        trusted_ips.append(prerender_service_ip)
    
    # 从环境变量添加TRUSTED_IPS列表
    trusted_ips_env = os.environ.get('TRUSTED_IPS', '')
    if trusted_ips_env:
        for ip in trusted_ips_env.split(','):
            ip = ip.strip()
            if ip:
                trusted_ips.append(ip)
    
    # 动态获取Docker网络中容器的实际IP
    try:
        import socket
        
        # 解析容器名到IP
        container_names = ['poetize-java', 'poetize-prerender', 'poetize-nginx']
        for container_name in container_names:
            try:
                ip = socket.gethostbyname(container_name)
                trusted_ips.append(ip)
            except socket.gaierror:
                logger.warning(f"无法解析容器名 {container_name}")
    except Exception as e:
        logger.warning(f"动态解析容器IP时出错: {e}")
    
    return trusted_ips

# 实时获取受信任的内部服务IP列表
def get_current_trusted_ips():
    """实时获取受信任的内部服务IP列表"""
    trusted_ips = [
        'localhost',
        '127.0.0.1'
    ]
    
    # 从环境变量添加静态IP
    java_service_ip = os.environ.get('JAVA_SERVICE_IP', 'poetize-java')
    if java_service_ip:
        trusted_ips.append(java_service_ip)
    
    prerender_service_ip = os.environ.get('PRERENDER_SERVICE_IP', 'poetize-prerender')
    if prerender_service_ip:
        trusted_ips.append(prerender_service_ip)
    
    trusted_ips_env = os.environ.get('TRUSTED_IPS', '')
    if trusted_ips_env:
        for ip in trusted_ips_env.split(','):
            ip = ip.strip()
            if ip:
                trusted_ips.append(ip)
    
    # 实时解析容器名到IP
    try:
        import socket
        container_names = ['poetize-java', 'poetize-prerender', 'poetize-nginx']
        for container_name in container_names:
            try:
                ip = socket.gethostbyname(container_name)
                if ip not in trusted_ips:
                    trusted_ips.append(ip)
            except socket.gaierror:
                pass  # 忽略解析失败
    except Exception:
        pass  # 忽略异常
    
    return trusted_ips

# 受信任的内部服务IP列表（初始化时获取）
TRUSTED_INTERNAL_IPS = get_trusted_internal_ips()

# FastAPI Security scheme
security = HTTPBearer(auto_error=False)

async def _fallback_ip_verification(client_ip: str, endpoint: str):
    """
    备用IP验证逻辑，当token验证失败时使用
    主要用于内部服务间调用的向后兼容
    """
    # 检查是否在内部网络（向后兼容）
    if is_internal_network_ip(client_ip):
        logger.warning(f"Token验证失败但IP在内部网段，允许访问（向后兼容）: {client_ip}, 请求: {endpoint}")
        logger.warning(f"建议为内部服务请求添加正确的认证token以增强安全性")
        return True

    # 检查是否在受信任IP列表中（向后兼容）
    current_trusted_ips = get_current_trusted_ips()
    if client_ip in current_trusted_ips:
        logger.warning(f"Token验证失败但IP在受信任列表，允许访问: {client_ip}, 请求: {endpoint}")
        return True

    # 都不满足，拒绝访问
    logger.error(f"Token验证失败且IP不在信任范围内，拒绝访问: {client_ip}, 请求: {endpoint}")
    raise HTTPException(
        status_code=status.HTTP_403_FORBIDDEN,
        detail={
            'code': 403,
            'message': '权限不足，需要站长或管理员权限',
            'data': None
        }
    )

async def admin_required(
    request: Request,
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security)
):
    """
    FastAPI依赖函数，验证请求是否来自站长(userType=0)或管理员(userType=1)
    使用Java后端的验证API进行权限校验

    注意：此依赖允许站长(userType=0)和管理员(userType=1)访问，都有完全的管理权限
    认证逻辑：优先验证管理员token，IP验证作为辅助安全措施（与Java端逻辑保持一致）
    """
    # 开始时间
    start_time = time.time()
    
    # 获取客户端IP
    client_ip = request.client.host
    x_forwarded_for = request.headers.get('X-Forwarded-For')
    
    # 如果请求来源是内部受信任网络（如Nginx反向代理）且提供了X-Forwarded-For，则使用其中的第一个IP
    if x_forwarded_for and is_internal_network_ip(request.client.host):
        original_ip = client_ip
        client_ip = x_forwarded_for.split(',')[0].strip()
    elif x_forwarded_for:
        logger.warning(f"X-Forwarded-For: {x_forwarded_for}")
    # 记录API访问信息
    endpoint = request.url.path

    # 优先验证：检查是否来自Docker内部网络且带有正确标识（与Java端逻辑一致）
    internal_service = request.headers.get('X-Internal-Service')
    admin_flag = request.headers.get('X-Admin-Request')

    # 如果是内部网络请求且带有正确的标识头，直接通过（与Java端逻辑一致）
    if (is_internal_network_ip(client_ip) and admin_flag == 'true' and
        internal_service in ['poetize-java', 'poetize-prerender', 'poetize-nginx', 'poetize-python']):
        return True

    # 主要验证：token验证（优先进行，与Java端逻辑一致）
    # 从请求中获取token
    token = None
    
    # 从Authorization头获取token
    if credentials:
        token = credentials.credentials
        # 适配新的HMAC签名token格式，显示更多字符用于调试
        token_preview = f"{token[:20]}...{token[-8:]}" if len(token) > 30 else token

    # 也从请求参数中检查token
    if not token:
        token = request.query_params.get('token')
        if token:
            token_preview = f"{token[:20]}...{token[-8:]}" if len(token) > 30 else token

    # 从cookie中检查token
    if not token:
        token = request.cookies.get('Admin-Token') or request.cookies.get('User-Token')
        if token:
            token_preview = f"{token[:20]}...{token[-8:]}" if len(token) > 30 else token
    
    # 没有token时尝试备用验证
    if not token:
        logger.warning(f"管理员API缺少认证token, IP: {client_ip}, API: {endpoint}")
        logger.warning(f"请求头: Authorization={credentials}, Cookie检查: Admin-Token={request.cookies.get('Admin-Token', '无')}, User-Token={request.cookies.get('User-Token', '无')}")
        # 尝试备用IP验证
        return await _fallback_ip_verification(client_ip, endpoint)
        
    try:
        # 处理token - 添加兼容性，确保Java后端也能接受不带Bearer前缀的token
        # 如果token已经有Bearer前缀，直接使用；否则增加Bearer前缀
        java_token = token
        if not java_token.startswith('Bearer '):
            java_token = f"Bearer {token}"
        
        headers = {
            'Authorization': java_token,  # 添加Bearer前缀，确保Java后端能正确识别token
            'X-Forwarded-For': client_ip,  # 将客户端IP传递给Java后端
            'X-Internal-Service': 'poetize-python',
            'X-Admin-Request': 'true',  # 标识为内部管理员请求，跳过额外的权限检查
            'User-Agent': 'poetize-python/1.0.0'
        }
        async with httpx.AsyncClient() as client:
            response = await client.get(JAVA_AUTH_URL, headers=headers, timeout=5)
        
        # 解析响应
        if response.status_code == 200:
            data = response.json()
            # 检查Java后端返回的权限验证结果
            if data.get('code') == 200 and data.get('data') is True:
                # Token验证通过，进行辅助安全检查

                # 检查限流（对于外部请求）
                if not is_internal_network_ip(client_ip):
                    if not admin_rate_limiter.is_allowed(client_ip):
                        logger.warning(f"IP {client_ip} 请求频率过高被拒绝访问管理员API")
                        raise HTTPException(
                            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                            detail={
                                'code': 429,
                                'message': '请求过于频繁，请稍后再试',
                                'data': None
                            }
                        )

                # 权限验证通过
                return True
            else:
                logger.warning(f"权限验证失败: {data.get('message', '未知错误')}")
                # Token验证失败，尝试备用验证方式
                return await _fallback_ip_verification(client_ip, endpoint)
        elif response.status_code == 401:
            # 处理未授权情况
            logger.warning(f"返回401未授权, IP: {client_ip}, API: {endpoint}")
            # Token无效，尝试备用验证方式
            return await _fallback_ip_verification(client_ip, endpoint)
        else:
            data = response.json() if response.content else {}
            logger.warning(f"权限验证失败: {data.get('message', '未知错误')}")
            # 其他错误，尝试备用验证方式
            return await _fallback_ip_verification(client_ip, endpoint)
            
    except HTTPException:
        # 重新抛出HTTPException
        raise
    except Exception as e:
        # 记录异常并尝试备用验证
        logger.error(f"验证权限时发生错误: {str(e)}, IP: {client_ip}, API: {endpoint}")
        return await _fallback_ip_verification(client_ip, endpoint)