# ---- Etapa 1: compila com Maven e gera o jar executável do Spring Boot ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- Etapa 2: imagem final leve, só com o JRE ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/onitama.jar ./onitama.jar
EXPOSE 8080
CMD ["java", "-jar", "onitama.jar", "--server.port=8080"]
