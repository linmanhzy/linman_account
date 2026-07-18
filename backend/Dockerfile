# 后端运行镜像
# 说明：jar 包已在 CD Runner 上用 `mvn package` 打好，
#       这里只负责"把 jar 装进 JRE 运行环境"，所以是个非常精简的镜像。
FROM eclipse-temurin:21-jre

WORKDIR /app

# 注意：CD 里 docker build 的上下文(context)是 backend/ 目录，
# 因此这里的路径相对于 backend/。把 Maven 打好的 jar 复制进来，
# 用通配符避免写死具体版本号。
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
