# 🔧 1단계: Gradle 빌드용 컨테이너
FROM gradle:8.7.0-jdk21-alpine AS builder

WORKDIR /app

# Gradle Wrapper 및 설정 파일 복사 (종속성 캐싱을 위해)
COPY gradlew .
COPY gradle gradle/
COPY build.gradle settings.gradle ./

# Gradle 종속성 다운로드 및 캐싱
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 전체 소스 복사
COPY src ./src

# 실행 가능한 JAR 파일 빌드
RUN ./gradlew clean bootJar -x test --no-daemon

# 🔧 2단계: 경량 런타임 이미지 (JRE only)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=production
ENV TZ=Asia/Seoul

COPY --from=builder /app/build/libs/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# HEALTHCHECK (선택)
# HEALTHCHECK --interval=30s --timeout=5s CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
