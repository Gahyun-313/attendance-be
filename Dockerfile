# ------------------------------------------------------------------------------
# 1. build stage
# ------------------------------------------------------------------------------
# Gradle + JDK 들어있는 무거운 이미지 - 여기서만 빌드하고 최종 이미지에는 남지기 않는다.

FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .

# 실행 가능한 jar만 있으면 되므로 bootJar로 범위를 좁힌다.
RUN ./gradlew bootJar --no-daemon

# ------------------------------------------------------------------------------
# 2. runtime stage
# ------------------------------------------------------------------------------
# JDK 전체가 아니라 JRE(실행 전용)만 있는 가벼운 이미지 - 빌드 도구는 여기 없어도 된다.

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# build stage에서 만든 jar 파일만 복사 - Gradle 캐시/소스 코드는 최종 이미지에 안 남는다.
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# 기본 프로파일은 prod(컨테이너 실행용, application-prod.yml)
# docker-compose나 AWS에서 필요하면 이 값을 환경변수로 덮어쓸 수 있다.
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]

