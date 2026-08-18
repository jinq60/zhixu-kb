# =====================================================================
# 知序管理后台 - 前端镜像
# 多阶段构建：Node 20 构建 → Nginx 托管（含 /api 反向代理）
# =====================================================================
FROM node:20-bookworm AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build-only

# ---------- 运行镜像 ----------
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 为非 root 用户准备可写缓存/日志/PID 目录
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    mkdir -p /var/cache/nginx /var/log/nginx /run && \
    chown -R nginx:nginx /var/cache/nginx /var/log/nginx /run && \
    chmod -R 755 /var/cache/nginx /var/log/nginx /run && \
    sed -i 's/^user nginx;/user nginx nginx;/' /etc/nginx/nginx.conf

# 使用 nginx 官方镜像的非 root 用户运行
USER nginx

EXPOSE 80
