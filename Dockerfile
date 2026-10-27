# 빌드 스테이지 — wrapper jar가 gitignore라 Maven 공식 이미지로 빌드한다.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Render 무료 플랜 메모리(512MB)에 맞춰 힙을 컨테이너 메모리 비율로 제한한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
