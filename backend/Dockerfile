FROM gradle:8.7-jdk17 AS builder
WORKDIR /home/gradle/src
COPY . .
RUN gradle --no-daemon clean shadowJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /home/gradle/src/build/libs/smart-feeder-backend-all.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
