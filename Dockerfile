FROM node:22-alpine AS frontend
WORKDIR /web
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /src
COPY backend/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /web/dist ./src/main/resources/static
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-build /src/target/hire-cockpit-*.jar app.jar
COPY --from=frontend /web/dist ./static
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
