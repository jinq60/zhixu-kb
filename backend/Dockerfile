# =====================================================================
# 知序智能知识库 - 后端镜像
# 多阶段构建：Maven 构建 jar → JRE 8 运行
# =====================================================================
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true

COPY src ./src
RUN mvn -q -B -DskipTests package

# ---------- 运行镜像 ----------
FROM eclipse-temurin:8-jre
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar
# /app/logs 必须预建并归属 app 用户：named volume 首挂时按镜像内目录属主初始化，
# 否则 volume 根目录为 root 属主，非 root 容器写 JSON 日志会 Permission denied
RUN mkdir -p /app/uploads/images /app/logs && \
    groupadd -r app && useradd -r -g app -d /app -s /sbin/nologin app && \
    chown -R app:app /app

USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
