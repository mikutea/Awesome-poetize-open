<p align="center">
  <a href="#">
    <img src="poetize_picture/首页1.jpg" alt="Logo" width="100%">
  </a>

<h3 align="center">POETIZE 最美博客（AGPL 分支 · LeapYa 维护）</h3>
  <p align="center">
    让内容创作与社交体验更美好
    <br />
    <br />
    <a href="快速开始">快速部署</a>
    ·
    <a href="部署文档">部署文档</a>
    ·
    <a href="#开发指南">二次开发</a>
  </p>
  <p align="center">
   <img src="https://img.shields.io/badge/license-AGPL--3.0-%3CCOLOR%3E.svg" alt="AGPL License">
   <img src="https://img.shields.io/badge/language-java-%23B07219.svg" alt="Java">
   <img src="https://img.shields.io/badge/language-python-%233572A5.svg" alt="Python">
   <img src="https://img.shields.io/badge/language-dockerfile-%23384D54.svg" alt="Dockerfile">
  </p>
</p>

## 目录

- [项目简介](#项目致谢)
- [项目预览](#项目预览)
- [快速开始](#快速开始)
- [部署文档](#部署文档)
- [贡献与许可](#贡献与许可)
- [开发指南](#开发指南)
- [技术栈](#技术栈)
- [联系方式](#联系方式)
- [版权说明](#版权说明)

## 项目简介

本项目基于开源项目 [POETIZE最美博客](https://gitee.com/littledokey/poetize) 功能扩展和定制化开发，历时半年，这是一个集内容创作、社交互动与技术优化于一体的现代化博客系统，非常适合个人建站和内容创作者使用。

<p align="center">
  <img src="poetize_picture/首页.png" alt="首页" width="100%">
</p>

<p align="center">博客首页 - 展示个人创作与生活点滴</p>

<p align="center">
  <img src="poetize_picture/首页1.jpg" alt="文章展示" width="49%">
  <img src="poetize_picture/首页2.jpg" alt="社交功能" width="49%">
</p>

<p align="center">左：内容布局展示 | 右：社交功能体验</p>

#### **本分支新增/优化功能**

1. ✅ 一键部署脚本 —— 一行命令自动完成环境配置、HTTPS配置和服务启动
2. ✅ 后台权限管理 —— 支持多角色分级管理，提升安全性
3. ✅ 多邮箱服务支持 —— 可配置多邮箱，提升邮件送达率
4. ✅ 第三方登录集成 —— 支持GitHub、Google、Twitter、Yandex、Gitee平台登录
5. ✅ 机器人验证功能 —— 集成滑动验证码，防止恶意注册
6. ✅ SEO优化与预渲染 —— 自动生成sitemap、robots.txt及页面预渲染，极大提升搜索引擎收录与SEO效果
7. ✅ 看板娘优化 —— Live2D看板娘可自定义、支持AI互动
8. ✅ 导航栏优化 —— 支持自定义导航栏，布局更美观
9. ✅ 评论体验优化 —— 评论内容自动保存，未登录也不丢失
10. ✅ 增加兰空图床、简单图床的存储支持 —— 支持多种图片上传方式
11. ✅ AI翻译 —— 支持中英互译，可用本地或API模型
12. ✅ 页脚优化 —— 页脚信息更丰富、可自定义
13. ✅ 图片压缩和转换WebP格式 —— 自动压缩图片，提升网站加载速度
14. ✅ 智能摘要 —— 自动生成文章摘要，提升阅读体验
15. ✅ 暗色模式优化、定时暗色模式 —— 支持夜间自动切换暗色主题，优化暗色模式
16. ✅ 灰色模式 —— 支持全站灰色纪念模式
17. ✅ 自定义错误页面 —— 提供友好的404、403等错误页面
18. ✅ 字体文件CDN化 —— 支持字体文件外部化存储与动态加载，可配置单一/分块字体模式，自定义Unicode范围，大幅减少网站带宽占用
19. ✅ 将MD5密码哈希升级为BCrypt算法 —— 修复密码安全漏洞
20. ✅ 评论区重构、优化其楼层计算算法优化 —— 对评论区进行重构，引入懒加载机制以提升页面加载速度，并使用深度优先遍历算法优化评论楼层计算逻辑，提高渲染性能
21. ✅ Redis缓存优化 —— 大部分接口使用Redis缓存，提升性能
22. ✅ 实现token签名算法HMAC-SHA256认证 —— 完全替换简单UUID token，新增防伪造、防篡改、防重放攻击能力

更多功能...

## 快速开始

```bash
# 你只需要输入域名邮箱即可
bash <(curl -sL install.leapya.com)
```

无需手动配置Docker、编译代码或设置环境变量，脚本会自动处理所有细节，包括:

* Docker环境检测与安装
* 数据库初始化
* 服务编排与启动
* 自动HTTPS配置

## 部署文档

### 架构概览

系统采用容器化部署，七大核心服务：

1. 主站前端 (Vue 2)
2. 聊天室前端 (Vue 3)
3. Java 后端（Java 21）
4. Python 后端（Python 3.9+）
5. 数据库（MariaDB 11，兼容MySQL 5.7）
6. Nginx 反向代理
7. Certbot SSL 证书

> **注意**: 如需使用MySQL替代MariaDB，请查看本项目的开发指南

### 部署流程

#### 1. 服务器要求

- 操作系统：推荐 Debian 10.3.3+ 或 Ubuntu 18.04+，详见下方测试表
- 内存：建议 2GB 及以上（2GB内存环境将自动配置2GB交换空间）
- 硬盘：建议 30GB 及以上，至少需要10G

##### 系统兼容性测试结果

| 操作系统类型          | CPU  | 内存 | 存储 | 测试结果  |
| --------------------- | ---- | ---- | ---- | --------- |
| Ubuntu 18.04+ x64     | 1核+ | 1G+  | 30GB | ✅ 推荐   |
| Debian 10+ x64        | 1核+ | 1G+  | 30GB | ✅ 推荐   |
| CentOS 7/8+ x64       | 1核+ | 1G+  | 30GB | ✅ 推荐   |
| RHEL/Rocky/AlmaLinux  | 1核+ | 1G+  | 30GB | ✅ 支持   |
| Fedora/Oracle Linux   | 1核+ | 1G+  | 30GB | ✅ 支持   |
| Amazon Linux          | 1核+ | 1G+  | 30GB | ✅ 支持   |
| 阿里云/腾讯云 Linux   | 1核+ | 1G+  | 30GB | ✅ 支持   |
| 麒麟/统信UOS/Deepin   | 1核+ | 1G+  | 30GB | ✅ 支持   |
| openEuler/EulerOS     | 1核+ | 1G+  | 30GB | ✅ 支持   |
| Alpine/Arch Linux     | 1核+ | 1G+  | 30GB | ✅ 支持   |
| openSUSE              | 1核+ | 1G+  | 30GB | ✅ 支持   |
| Windows Server/桌面版 | -    | -    | -    | ❌ 不支持 |
| CentOS 6.x 及更早系统 | -    | -    | -    | ❌ 不支持 |

#### 2. 环境准备

* 域名解析到服务器
* 开放 80 TCP 和443 UDP/TCP 端口

#### 3. 部署步骤

##### **快速部署方式**

```bash
# 以下方式任选其一即可
# 方式一：交互模式：
bash <(curl -sL install.leapya.com)

# 方式二：非交互模式(替换成自己的域名，每个域名使用-d隔开):
bash <(curl -sL install.leapya.com) -d 域名.com -d www.域名.com

# 方式三：或克隆本仓库使用deploy.sh脚本部署（交互模式）：
git clone https://github.com/LeapYa/Awesome-poetize-open.git && sudo chmod +x deploy.sh && sudo ./deploy.sh

# 方式四：或克隆本仓库使用deploy.sh脚本部署（非交互模式，替换成自己的域名，每个域名使用-d隔开)：
git clone https://github.com/LeapYa/Awesome-poetize-open.git && sudo chmod +x deploy.sh && sudo ./deploy.sh -d 域名.com -d www.域名.com
```

##### **启动顺序**

* MySQL服务 → 数据初始化 → 后端服务 → 前端构建 → Nginx → 证书申请

### 访问方式

* 主站：`http(s)://域名/`
* 聊天室：`http(s)://域名/im`
* 管理后台：`http(s)://域名/admin`
* 默认账号密码：`Sara / aaa`

### OAuth代理（可选）

我们第三方登录的时候想支持国外第三方登录平台（如谷歌、github等），需要配置海外代理服务器才可以正常第三方登录，否则会失败，具体如何配置请查看[OAuth代理配置说明文档](OAuth代理配置说明.md)

### Ollama翻译模型配置（可选）

如果你想启用本地AI翻译功能，我们也集成了Ollama模型支持。只需要取消 `docker-compose.yml`中的相关注释即可：

```bash
# 编辑docker-compose.yml文件
vim docker-compose.yml

# 找到"# Ollama翻译模型服务"部分，取消注释即可启用
# 模型会自动下载qwen3:0.6b轻量级翻译模型
```

启用后将提供：

* 本地化AI翻译服务
* 无需依赖第三方翻译API
* 支持中英文互译
* 模型数据持久化存储

#### 自定义模型配置

如果你不想使用默认的 `qwen3:0.6b`模型，可以修改为其他支持的模型：

1. **修改docker-compose.yml**

   ```yaml
   # 找到OLLAMA_MODELS环境变量，修改为你想要的模型
   - OLLAMA_MODELS=deepseek-r1:8b  # 例如改为deepseek-r1:8b
   ```
2. **修改Dockerfile配置**

   ```bash
   # 编辑docker/translation_model/Dockerfile
   vim docker/translation_model/Dockerfile

   # 将其中的qwen3:0.6b替换为你想要的模型名称，如deepseek-r1:8b、qwen3:8b等
   ```

**更多模型选择：**

你可以访问 [Ollama官方模型库](https://ollama.com/library) 查看所有可用的模型，包括：

* **推理模型**: `deepseek-r1` (1.5b, 7b, 8b, 14b, 32b, 70b, 671b)
* **通用模型**: `qwen3` (0.6b, 1.7b, 4b, 8b, 14b, 30b, 32b), `llama3.2` (1b, 3b)
* **代码模型**: `qwen2.5-coder` (0.5b, 1.5b, 3b, 7b, 14b, 32b)
* **视觉模型**: `llama3.2-vision` (11b, 90b), `qwen2.5vl` (3b, 7b, 32b, 72b)

选择模型时请考虑服务器配置，较大的模型需要更多内存和计算资源。

**量化版本说明：**

Ollama默认提供的是4位量化版本的模型，体积小但精度相对较低。如果需要更高的翻译准确度，可以使用其他量化版本：

```yaml
# 默认4位量化（体积小，速度快）
- OLLAMA_MODELS=qwen3:8b

# 8位量化（准确度更高，体积适中）
- OLLAMA_MODELS=qwen3:8b-q8_0

# 16位量化（最高准确度，体积最大）
- OLLAMA_MODELS=qwen3:8b-fp16
```

**量化版本对比：**

* **4位量化（默认）**: 体积最小，速度最快，适合资源受限环境
* **8位量化（q8_0）**: 平衡准确度与资源消耗，推荐用于翻译任务
* **16位量化（fp16）**: 最高准确度，需要充足内存和存储空间

### 常用命令

```bash
# 容器状态
docker ps -a

# 查看日志
docker logs poetize-nginx

# 服务管理（docker-compose旧版本的命令是docker compose）
docker compose restart
docker compose down
docker compose up -d

# HTTPS手动配置
docker exec poetize-nginx /enable-https.sh

# 升级项目
poetize -update

# 迁移博客
poetize -qy
```

### 注意事项

1. 立即修改默认管理员密码
2. 确保服务器资源充足
3. HTTPS需正确域名解析

### 故障排查

1. **服务启动问题**

   * Docker服务状态
   * 端口占用检查
   * 配置文件验证
2. **HTTPS配置失败**

   * 域名解析验证
   * 80端口访问性
   * 证书目录权限

### 脚本说明

本项目有多个脚本，主要用于博客的安装、升级、迁移

#### 部署脚本（deploy.sh）

用于将博客部署到当前服务器，会自动帮你安装docker、配置国内镜像源、配置https等，以下是基本用法：

```bash
# 交互式部署
./deploy.sh

# 参数部署
./deploy.sh -d example.com -d www.example.com

# 查看帮助/所有子命令
./deploy.sh -h
```

#### 迁移脚本（migrate.sh）

用于将当前服务器上的博客一键迁移到另一台服务器，以下是基本用法：

```bash
# 在源服务器（已有正在运行的博客）执行
chmod +x ./migrate.sh && ./migrate.sh
```

#### 状态查看、升级脚本（poetize）

用于将当前服务器的博客升级、查看状态等

```bash
# 查看帮助/所有子命令
poetize -h

# 升级到最新版本（拉取最新配置与镜像并平滑重启）
poetize -update

# 快速迁移引导（在源服务器执行，内部调用 migrate.sh）
poetize -qy
```

`poetize` 命令由一键安装脚本安装（bash <(curl -sL install.leapya.com)）。若未安装，可直接在项目根目录使用 `deploy.sh`、`migrate.sh` 完成部署与迁移。

#### 关于国内环境部署

国内环境存在Docker安装困难或网络受限的情况，为确保顺利部署，项目已提供完整的离线部署方案。只需从Release页面下载离线资源包并按以下结构放置：

##### 1. 使用deploy.sh脚本：

- 项目中提供的**deploy.sh**脚本已经包含了国内环境的配置和加速源设置。
- 该脚本会自动配置npm使用淘宝镜像源，并增加网络参数以提高下载速度。
- 通过执行**deploy.sh**，可以自动完成环境的初始化和依赖的安装。

##### 2. 离线资源包：

- 从Release页面下载离线资源包。
- 按照以下结构放置资源包：

  ```
  offline/
  ├── docker.tar.gz           # Docker离线安装包
  ├── docker-compose          # Docker Compose二进制文件 
  └── images/                 # Docker镜像目录
     ├── mysql.tar           # MySQL数据库镜像
     ├── nginx.tar           # Nginx反向代理镜像
     ├── java.tar            # Java后端服务镜像
     └── python.tar          # Python后端服务镜像
  ```

##### 3. 执行部署：

- 确保所有资源包和配置文件已正确放置。
- 运行以下命令以启动部署：
  ```
  chmod +x ./deploy.sh && sudo ./deploy.sh
  ```

通过这些步骤，您可以在中国国内环境中顺利部署项目，避免网络限制带来的问题。

## 贡献与许可

* 原作者：Sara (POETIZE最美博客)
* Fork版本开发：LeapYa
* 开源协议：遵循原项目AGPL协议

## 开发指南

### 环境要求

* **Node.js 14+** - 前端开发
* **JDK 21** - Java后端开发
* **Maven/Gradle** - Java项目构建
* **Python 3.9+** - Python后端开发
* **Docker & Compose** - 容器化部署
* **Git** - 版本控制
* **数据库客户端** - 开发调试

### 项目结构

<details>
<summary>
项目主要目录结构（点击展开完整结构）

```
├── deploy.sh                # 部署脚本
├── docker-compose.yml       # docker服务编排文件
├── poetize-im-ui/           # 聊天室UI (Vue3)
├── poetize-server/          # Java后端
├── poetize-ui/              # 博客系统UI (Vue2)
├── py/                      # Python服务
├── split_font/              # 分割字体文件目录
└── README.md                # 项目文档
```

</summary>

```
.
├── deploy.sh                # 部署脚本
├── docker-compose.yml       # 服务编排
├── mysql/                   # MySQL配置
├── nginx/                   # Nginx配置
├── poetize-im-ui/           # 聊天室UI (Vue3)
│   ├── package.json         # 聊天室依赖配置
│   ├── package-lock.json    # 聊天室依赖版本锁定文件
│   └── src/                 # 聊天室源代码
│       ├── assets/          # 静态资源
│       ├── components/      # UI组件
│       ├── router/          # 路由配置
│       ├── store/           # 状态管理
│       ├── utils/           # 工具类
│       │   ├── font-loader.js  # 字体动态加载器
│       │   ├── common.js       # 通用工具
│       │   └── request.js      # 请求封装
│       └── main.js          # 主入口
├── poetize-server/          # Java后端
│   ├── pom.xml              # 主项目Maven配置
│   ├── package.json         # 依赖配置
│   ├── package-lock.json    # 依赖版本锁定文件
│   ├── sql/                 # SQL脚本目录
│   │   ├── poetry.sql       # 数据库初始化脚本
│   │   └── update_nav_config.sql # 导航配置更新脚本
│   └── poetry-web/          # Web模块
│       ├── pom.xml          # Web模块Maven配置
│       ├── src/             # Java源代码目录
│       ├── config/          # 配置文件目录 
│       └── data/            # 数据文件目录
├── poetize-ui/              # 博客系统UI (Vue2)
│   ├── package.json         # 博客UI依赖配置
│   ├── package-lock.json    # 博客UI依赖版本锁定文件
│   └── src/                 # 博客UI源代码
│       ├── assets/          # 静态资源
│       │   ├── css/         # 样式文件
│       │   │   └── index.css # 主样式
│       ├── components/      # UI组件
│       ├── router/          # 路由配置
│       ├── store/           # 状态管理
│       ├── utils/           # 工具类
│       │   ├── font-loader.js  # 字体动态加载器
│       │   ├── common.js       # 通用工具
│       │   └── request.js      # 请求封装
│       └── main.js          # 主入口
├── py/                      # Python服务
│   ├── main.py              # Python主应用入口
│   ├── config.py            # 配置文件处理
│   ├── auth_decorator.py    # 认证装饰器
│   ├── captcha_api.py       # 验证码服务
│   ├── email_api.py         # 邮件服务
│   ├── py_three_login.py    # 第三方登录
│   ├── seo_api.py           # SEO优化服务
│   ├── visit_stats_api.py   # 访问统计
│   ├── web_admin_api.py     # 管理员API
│   ├── data/                # 数据配置目录
│   ├── requirements.txt     # Python依赖列表
│   ├── static/              # 静态资源
│   ├── translation_model/   # 机器翻译模型目录
│   │   ├── translation_api.py     # 翻译API
│   │   ├── translation_service.py # 翻译服务
│   │   ├── models/          # 预训练模型目录
│   │   ├── data/            # 翻译数据
│   │   └── utils.py         # 工具函数
│   └── third_login_config.json # 第三方登录配置
├── split_font/              # 分割字体文件目录
│   ├── font_chunks/         # 分块字体文件
│   │   ├── font.base.woff2  # 基础字符字体
│   │   ├── font.level1.woff2 # 一级常用汉字字体
│   │   ├── font.level2.woff2 # 二级常用汉字字体
│   │   └── font.other.woff2 # 其他字符字体
│   └── unicode_ranges.json  # Unicode范围配置文件
└── README.md                # 项目文档
```

</details>

### 前端开发

1. **更换测试环境的访问API(生产环境中需要更改回去)**

   修改poetize-ui/src/utils/constant.js

   ```
   // 测试环境
   baseURL: "http://localhost:8081",
   pythonBaseURL: "http://localhost:5000", // Python服务URL
   imBaseURL: "http://localhost:81/im",
   webURL: "http://localhost",

   // 生产环境(本地开发需要注释掉)
   // webURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : ''),
   // baseURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : '') + "/api",
   // pythonBaseURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : '') + "/python",
   // imBaseURL: location.protocol + "//" + location.hostname + (location.port ? ':' + location.port : '') + "/im",
   ```
2. **依赖安装**

   ```bash
   cd poetize-ui
   npm install --legacy-peer-deps
   ```
3. **开发服务**

   ```bash
   npm run serve
   ```

### Java后端开发

1. **导入项目** - 使用IntelliJ IDEA或Eclipse导入poetize-server目录
2. **主要模块**

   - `poetry-web`: 核心业务模块，含控制器、服务和实体类
   - `sql`: 数据库初始化和更新脚本
3. **配置修改**

   - `poetry-web/src/main/resources/application.yml`
4. **启动应用** - 运行 `com.PoetizeApplication` 主类
5. **构建打包**

   ```bash
   # 在poetize-server根目录执行
   mvn clean package
   ```
6. **打包结果** - 生成的JAR文件位于 `poetry-web/target/` 目录

### Python后端开发

Python服务提供以下关键功能：

- 第三方登录集成（GitHub、Google等）
- 机器翻译服务
- SEO优化（站点地图生成）
- 邮件服务和验证码发送
- 内容管理API

1. **虚拟环境**

   ```bash
   python -m venv venv
   source venv/bin/activate  # Linux/macOS
   venv\Scripts\activate     # Windows
   ```
2. **依赖安装**

   ```bash
   pip install -r requirements.txt
   ```
3. **配置修改** - 修改 `third_login_config.json`配置第三方登录
4. **启动服务** - 运行主入口脚本

   ```bash
   uvicorn main:app --port 5000 --reload
   ```
5. **翻译服务** - 位于translation_model目录，可单独运行

   ```bash
   cd translation_model
   python run_server.py
   ```

### 数据库

* 系统默认使用MariaDB 11（完全兼容MySQL 5.7）
* 参考docker-compose.yml中配置
* 表结构变更建议使用迁移工具

### 数据库选择说明

本项目默认采用MariaDB 11作为数据库，而非MySQL 5.7。这是基于以下考虑：

1. **性能优势** - MariaDB在高并发读写场景下通常表现更佳
2. **开源承诺** - MariaDB保持完全开源，没有商业许可限制
3. **兼容性保证** - 完全兼容MySQL的SQL语法和连接协议
4. **额外特性** - 提供更多存储引擎选项和优化器改进

虽然项目可以使用MySQL运行，但我们推荐使用MariaDB以获得更好的性能和未来的可扩展性。若必须使用MySQL，请参考下方切换步骤进行配置调整。两种数据库的数据文件和表结构完全兼容，可以无缝迁移。

#### 从MariaDB切换到MySQL的详细步骤

如需在开发或部署环境中使用MySQL替代MariaDB，按以下步骤操作：

1. **修改Java项目依赖（pom.xml）**

   ```xml
   <!-- 移除MariaDB依赖 -->
   <dependency>
     <groupId>org.mariadb.jdbc</groupId>
     <artifactId>mariadb-java-client</artifactId>
     <version>3.1.4</version>
     <!-- ... -->
   </dependency>

   <!-- 添加MySQL依赖 -->
   <dependency>
     <groupId>mysql</groupId>
     <artifactId>mysql-connector-java</artifactId>
     <version>8.0.33</version>
   </dependency>
   ```
2. **更新Java应用配置（application.yml）**

   ```yaml
   spring:
     datasource:
       # 从这个
       driver-class-name: org.mariadb.jdbc.Driver
       url: jdbc:mariadb://localhost:3306/poetize?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai

       # 改为这个
       driver-class-name: com.mysql.cj.jdbc.Driver
       url: jdbc:mysql://localhost:3306/poetize?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
   ```
3. **修改Docker镜像构建文件（docker/java/Dockerfile）**

   ```Dockerfile
   # 将
   RUN wget -O /app/libs/mariadb-java-client-3.1.4.jar https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/3.1.4/mariadb-java-client-3.1.4.jar

   # 改为
   RUN wget -O /app/libs/mysql-connector-java-8.0.33.jar https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar
   ```
4. **更新Docker Compose配置（docker-compose.yml）**

   ```yaml
   # MariaDB服务配置
   mysql:
     # 从这个
     image: mariadb:11.1
     # 改为这个
     image: mysql:8.0

     environment:
       # 从这些环境变量
       - MARIADB_ROOT_PASSWORD=root123
       - MARIADB_DATABASE=poetize 
       - MARIADB_USER=poetize
       - MARIADB_PASSWORD=poetize123

       # 改为这些
       - MYSQL_ROOT_PASSWORD=root123
       - MYSQL_DATABASE=poetize
       - MYSQL_USER=poetize
       - MYSQL_PASSWORD=poetize123
   ```

   还需要修改Java服务环境变量部分：

   ```yaml
   poetize-java:
     # ...
     environment:
       # 修改驱动类名
       - SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
       # 修改URL前缀
       - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/poetize?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
   ```
5. **Python服务配置修改**（如果使用Python服务连接数据库）

   ```yaml
   poetize-python:
     # ...
     environment:
       # 修改数据库类型
       - DB_TYPE=mysql
       # 可能需要添加或修改连接参数
       - DB_CHARSET=utf8mb4
   ```
6. **修改my.cnf配置**（MySQL 8.0和MariaDB 11虽然兼容，但有些特定配置可能不同）

   ```ini
   # my.cnf
   [mysqld]
   default-authentication-plugin=mysql_native_password  # MySQL 8.0需要此配置以支持旧密码认证
   # 其他配置...
   ```

这些修改完成后，系统将使用MySQL而非MariaDB作为数据库引擎。注意，MariaDB对MySQL的某些语法有扩展，如果您的SQL使用了这些扩展特性，切换时可能需要调整。

### 配置说明

* **docker-compose.yml** - 服务编排与环境变量
* **nginx/\*.conf** - 反向代理与负载均衡
* **mysql/conf/my.cnf** - 数据库引擎设置
* **应用配置** - Java和Python各自配置文件
* **敏感数据** - 密码、密钥不应提交，使用.gitignore或环境变量

## 技术栈

* **前端** - Vue2/Vue3、Element UI、Socket.io、Live2D
* **后端** - Spring Boot、MyBatis Plus、Fastapi、OAuth2.0
* **数据库** - MariaDB 11（兼容MySQL 5.7）
* **部署** - Docker、Docker Compose、Nginx、Shell脚本

## 联系方式

* **邮箱** - enable_lazy@qq.com 或 hi@leapya.com
* **问题反馈** - [GitHub Issues](https://github.com/LeapYa/Awesome-poetize-open/issues)

所有项目贡献者信息请参阅[贡献者](#-贡献与许可)部分。

### 部署方式

本地开发完成后，修改代码并重建Docker镜像，使用 `deploy.sh`脚本进行自动化部署。

## 📜 版权说明

本项目遵循GNU Affero General Public License v3.0 (AGPL-3.0)开源许可协议，详情请参阅[LICENSE](LICENSE)文件。
