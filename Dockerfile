FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/jbsolar-0.0.1-SNAPSHOT.jar app.jar
USER 10001
CMD ["java", "-jar", "app.jar"]
