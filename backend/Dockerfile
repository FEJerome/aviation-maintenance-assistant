# ==========================================
# 阶段一：构建 Spring Boot 应用
# ==========================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 先复制 pom.xml 并下载依赖，利用 Docker 缓存层
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# 阶段二：运行环境（仅 JRE，最小化镜像）
# ==========================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# 创建非 root 用户运行应用（安全最佳实践）
RUN addgroup --system appgroup && adduser --system appuser --ingroup appgroup

# 从构建阶段复制 JAR
COPY --from=builder /app/target/*.jar app.jar

# 创建 data 目录用于挂载 PDF 源文档
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# 启用虚拟线程（Java 21 默认行为，显式声明以自文档化）
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
