FROM node:18.20.8-bookworm-slim
# 声明构建时环境变量
ARG VUE_APP_POETIZE_AES_KEY
ENV VUE_APP_POETIZE_AES_KEY=$VUE_APP_POETIZE_AES_KEY

RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

# 设置工作目录，保持与运行时一致
WORKDIR /app/src 