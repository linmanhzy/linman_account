# 后端运行镜像
# 说明：jar 包已在 CD Runner 上用 `mvn package` 打好，
#       这里只负责"把 jar 装进 JRE 运行环境"，所以是个非常精简的镜像。
FROM eclipse-temurin:21-jre

# 安装 curl（健康检查用）+ 创建非 root 用户，合并为一个层减小体积
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appgroup \
    && useradd -r -g appgroup -d /app -s /sbin/nologin appuser

WORKDIR /app

# 注意：CD 里 docker build 的上下文(context)是 backend/ 目录，
# 因此这里的路径相对于 backend/。把 Maven 打好的 jar 复制进来，
# 用通配符避免写死具体版本号。
COPY target/*.jar app.jar

# 设置文件权限：appuser 只读
RUN chown -R appuser:appgroup /app && chmod 500 /app/app.jar

# 切换到非 root 用户
USER appuser

EXPOSE 8080

# 健康检查：每 30 秒 HTTP 检查一次，启动给 60 秒缓冲
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || curl -f http://localhost:8080/api/records || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
