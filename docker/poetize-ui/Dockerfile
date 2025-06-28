FROM node:16-slim



RUN apt-get update && \
    apt-get install -y git && \
    apt-get install -y curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 设置工作目录，保持与运行时一致
WORKDIR /app/src 