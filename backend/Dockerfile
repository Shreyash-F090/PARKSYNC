FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend ./backend
RUN mvn -f backend/pom.xml package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/target/parksync-backend.jar /app/parksync-backend.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/parksync-backend.jar"]