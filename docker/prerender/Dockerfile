FROM node:18-alpine

WORKDIR /app

RUN npm config set registry https://registry.npmmirror.com

# 安装依赖 puppeteer & express
RUN npm set progress=false && npm install express@4.19.2 body-parser@1.20.2 axios@1.6.7 markdown-it@14.0.0 highlight.js@11.9.0 cheerio@1.0.0-rc.12 critters@0.0.24 --save

# 复制 worker 源码
COPY worker.js .

CMD ["node", "worker.js"] 