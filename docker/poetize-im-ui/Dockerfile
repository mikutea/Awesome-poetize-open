FROM node:18.20.8-bookworm-slim


RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

# 设置工作目录，保持与运行时一致
WORKDIR /app/src 