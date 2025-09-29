# OAuth代理配置说明

为了解决国内服务器无法访问Google、GitHub、X(Twitter)、Yandex等海外OAuth服务的问题，我们可以通过境外反向代理服务器来访问这些服务，需要准备一台海外vps。

## 配置方式

### 1. 环境变量配置

在docker-compose.yml中或通过环境变量设置：

```bash
# OAuth API代理域名
OAUTH_PROXY_DOMAIN=https://auth.example.com

# OAuth回调代理域名（通常与OAUTH_PROXY_DOMAIN相同）
OAUTH_CALLBACK_DOMAIN=https://auth.example.com
```

### 2. 支持的平台

**需要代理的海外平台：**

- GitHub
- Google
- X (Twitter)
- Yandex

**不需要代理的国内平台：**

- Gitee
- QQ
- 百度

## 海外vps配置

### Nginx配置示例

部署Nginx后，配置如下路径映射：

```nginx
server {
    listen 443 ssl http2;
    server_name auth.example.com;
  
    # SSL配置
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
  
    # 通用代理设置
    proxy_http_version 1.1;
    proxy_ssl_server_name on;
    proxy_set_header Connection "";
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_redirect off;

    # ============================================================================
    # GitHub API代理
    # ============================================================================
  
    # Token交换端点
    location = /github/login/oauth/access_token {
        proxy_set_header Host github.com;
        proxy_pass https://github.com/login/oauth/access_token;
    }
  
    # 用户信息和邮箱API
    location /github/api/ {
        proxy_set_header Host api.github.com;
        proxy_pass https://api.github.com/;
    }

    # ============================================================================
    # Google API代理
    # ============================================================================
  
    # Token交换端点
    location /google/oauth2/ {
        proxy_set_header Host oauth2.googleapis.com;
        proxy_pass https://oauth2.googleapis.com/;
    }
  
    # 用户信息端点（统一使用v2接口）
    location = /google/oauth2/v2/userinfo {
        proxy_set_header Host www.googleapis.com;
        proxy_pass https://www.googleapis.com/oauth2/v2/userinfo;
    }

    # ============================================================================
    # X (Twitter) API代理
    # ============================================================================
  
    # OAuth 1.0和2.0的API端点
    location /x/api/ {
        proxy_set_header Host api.twitter.com;
        proxy_pass https://api.twitter.com/;
    }

    # ============================================================================
    # Yandex API代理
    # ============================================================================
  
    # Token交换端点
    location = /yandex/token {
        proxy_set_header Host oauth.yandex.com;
        proxy_pass https://oauth.yandex.com/token;
    }
  
    # 用户信息端点
    location /yandex/login/ {
        proxy_set_header Host login.yandex.ru;
        proxy_pass https://login.yandex.ru/;
    }
}
```

## 工作流程

1. **用户点击登录** → 后端返回官方授权URL `https://github.com/login/oauth/authorize`
2. **用户浏览器** → 直接访问GitHub授权页面（用户自己解决网络问题）
3. **用户授权** → GitHub直接回调到国内服务器 `https://your-domain.com/oauth/callback/github`
4. **后端Token交换** → 通过代理（我们自己搭建的）访问 `https://auth.example.com/github/login/oauth/access_token`
5. **后端用户信息** → 通过代理访问 `https://auth.example.com/github/api/user`

### 双端支持

系统同时支持Java后端和Python后端的OAuth代理：

- **Java端**: 通过 `OAuthClientServiceImpl` 处理OAuth流程
- **Python端**: 通过 `OAuthConfigManager` 动态配置代理URL
- **统一配置**: 两端共享相同的环境变量

## 注意事项

1. **域名证书**: 境外代理域名需要配置有效的SSL证书
2. **简化配置**: 只需要代理API端点，不需要Cookie重写和HTML处理
3. **回调配置**: OAuth应用的回调URL直接配置为国内域名
4. **网络稳定**: 境外代理服务器需要稳定的网络连接
5. **路径精确**: 确保代理路径与代码中的URL映射完全一致

## 测试验证

配置完成后，可以通过以下方式测试：

1. **测试后端API代理**:

   ```bash
   curl https://auth.example.com/github/api/user
   curl https://auth.example.com/google/oauth2/token
   ```
2. **测试完整OAuth流程**:

   - 访问你的登录页面，点击GitHub/Google登录
   - 用户浏览器会直接跳转到官方授权页面
   - 授权后回调到你的国内服务器
   - 检查后端日志，确认token和用户信息获取成功

## 故障排除

- **用户无法访问授权页**: 用户网络问题，需要用户自己解决（翻墙/海外网络）
- **Token获取失败**: 检查API代理路径配置和境外服务器连通性
- **用户信息获取失败**: 检查用户信息API的代理配置
- **代理连接失败**: 检查境外代理服务器的网络和SSL配置
