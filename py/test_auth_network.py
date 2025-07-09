#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试基于网段的内部服务认证功能
"""

import ipaddress
import os
from auth_decorator import is_internal_network_ip, get_current_docker_network, DOCKER_INTERNAL_NETWORKS

def test_network_detection():
    """
    测试网络检测功能
    """
    print("=== 测试基于网段的内部服务识别 ===")
    
    # 测试用例
    test_cases = [
        # Docker内部网络IP
        ('172.28.147.2', True, 'Nginx服务IP'),
        ('172.28.147.6', True, 'Python服务IP'),
        ('172.28.147.7', True, 'Java服务IP'),
        ('172.28.147.9', True, '预渲染服务IP'),
        
        # 本地回环IP
        ('127.0.0.1', True, '本地回环'),
        ('localhost', False, '主机名，需要解析'),
        
        # 外部IP
        ('8.8.8.8', False, '外部DNS服务器'),
        ('192.168.1.100', True, '私有网络IP'),
        ('10.0.0.50', True, 'Docker默认网段'),
        ('172.17.0.2', True, 'Docker默认网段'),
        
        # 公网IP
        ('203.208.60.1', False, '公网IP'),
        ('114.114.114.114', False, '公网DNS'),
    ]
    
    print(f"当前Docker网段: {get_current_docker_network()}")
    print(f"预定义内部网段: {DOCKER_INTERNAL_NETWORKS}")
    print()
    
    for ip, expected, description in test_cases:
        try:
            result = is_internal_network_ip(ip)
            status = "✅ 通过" if result == expected else "❌ 失败"
            print(f"{status} {ip:15} -> {result:5} (期望: {expected:5}) - {description}")
        except Exception as e:
            print(f"❌ {ip:15} -> 异常: {e} - {description}")
    
    print()

def test_environment_variables():
    """
    测试环境变量配置
    """
    print("=== 测试环境变量配置 ===")
    
    # 检查关键环境变量
    env_vars = [
        'DOCKER_SUBNET',
        'JAVA_SERVICE_IP',
        'PRERENDER_SERVICE_IP',
        'TRUSTED_IPS'
    ]
    
    for var in env_vars:
        value = os.environ.get(var, '未设置')
        print(f"{var}: {value}")
    
    print()

def test_network_parsing():
    """
    测试网络解析功能
    """
    print("=== 测试网络解析功能 ===")
    
    # 测试不同格式的网段
    test_networks = [
        '172.28.147.0/28',
        '192.168.1.0/24',
        '10.0.0.0/8',
        '127.0.0.0/8'
    ]
    
    test_ip = '172.28.147.6'
    
    for network_str in test_networks:
        try:
            network = ipaddress.ip_network(network_str, strict=False)
            ip_addr = ipaddress.ip_address(test_ip)
            is_in_network = ip_addr in network
            print(f"IP {test_ip} 在网段 {network_str} 内: {is_in_network}")
        except Exception as e:
            print(f"解析网段 {network_str} 失败: {e}")
    
    print()

def main():
    """
    主测试函数
    """
    print("Docker网段认证测试工具")
    print("=" * 50)
    
    test_environment_variables()
    test_network_parsing()
    test_network_detection()
    
    print("测试完成！")
    print()
    print("使用说明:")
    print("1. 内部服务请求应该在Docker网段内（如172.28.147.x）")
    print("2. 建议在请求头中添加 X-Internal-Service 标识")
    print("3. 支持的内部服务标识: poetize-java, poetize-prerender, poetize-nginx")
    print("4. 外部请求需要提供有效的认证token")

if __name__ == '__main__':
    main()