# 前端 Dockerfile — 多阶段构建
# 阶段1：Node.js 构建 React 应用
# 阶段2：Nginx 提供静态文件服务 + 反向代理后端 API

# ========== 阶段1：构建前端 ==========
FROM node:20-alpine AS builder
WORKDIR /app

# 先复制依赖文件，利用 Docker 缓存层
COPY package.json package-lock.json ./
RUN npm ci

# 复制源代码并构建
COPY . .
RUN npm run build

# ========== 阶段2：Nginx 服务 ==========
FROM nginx:alpine

# 从构建阶段复制产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
