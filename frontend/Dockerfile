# 阶段一：构建前端生产包
FROM node:20-alpine AS builder

WORKDIR /app

# 先复制 package 文件并安装依赖，利用 Docker 缓存层
COPY package*.json ./
RUN npm install

# 复制源码并构建
COPY . .
RUN npm run build

# 阶段二：Nginx 托管静态资源
FROM nginx:alpine

# 复制构建产物到 Nginx 默认目录
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
