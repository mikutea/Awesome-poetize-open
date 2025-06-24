#!/bin/sh
# certbot入口脚本 - 处理证书申请和自动续期

# 设置变量（这些值将在部署时由deploy.sh脚本替换）
WEBROOT_PATH="/usr/share/nginx/html"

# 权限修复函数
fix_cert_permissions() {
  echo "设置证书文件权限，让nginx用户(UID 101)能够读取..."
  if [ -d "/etc/letsencrypt/live" ]; then
    # 设置目录权限为755，让nginx用户可以进入目录
    find /etc/letsencrypt/live -type d -exec chmod 755 {} \;
    find /etc/letsencrypt/archive -type d -exec chmod 755 {} \; 2>/dev/null || true
    
    # 设置证书文件权限为644，让nginx用户可读
    find /etc/letsencrypt/live -name "*.pem" -exec chmod 644 {} \;
    find /etc/letsencrypt/archive -name "*.pem" -exec chmod 644 {} \; 2>/dev/null || true
    
    # 设置关键目录权限
    chmod 755 /etc/letsencrypt/live
    chmod 755 /etc/letsencrypt/archive 2>/dev/null || true
    
    echo "证书文件权限设置完成"
  else
    echo "警告: 证书目录不存在，跳过权限设置"
  fi
}

# 打印信息
echo "==== Certbot 证书管理服务启动 ===="
echo "Web根目录: $WEBROOT_PATH"

# 首次申请证书
echo "开始申请证书..."
echo "执行: certbot certonly --webroot --webroot-path=$WEBROOT_PATH --email your-email@example.com --agree-tos --no-eff-email --force-renewal --expand -d example.com -d www.example.com"
certbot certonly --webroot \
  --webroot-path=$WEBROOT_PATH \
  --email your-email@example.com \
  --agree-tos \
  --no-eff-email \
  --force-renewal \
  --expand \
  -d example.com -d www.example.com

# 检查证书申请结果
if [ $? -eq 0 ]; then
  echo "证书申请成功!"
  fix_cert_permissions
else
  echo "证书申请失败，将继续尝试自动续期..."
fi

# 设置自动续期
echo "设置自动续期..."
trap exit TERM

# 自动续期循环
while :; do
  echo "检查证书续期..."
  certbot renew --quiet
  
  # 续期后修复权限
  if [ $? -eq 0 ]; then
    echo "续期成功，修复证书文件权限..."
    fix_cert_permissions
  fi
  
  # 休眠12小时
  echo "证书检查完成，12小时后再次检查..."
  sleep 12h
done 