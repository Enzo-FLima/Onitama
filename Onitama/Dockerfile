# ---- Etapa 1: compila com Maven e baixa as dependências ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q compile dependency:copy-dependencies -DoutputDirectory=target/lib

# ---- Etapa 2: imagem final leve, só com o JRE ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/lib ./lib
EXPOSE 8080
CMD ["java", "-cp", "classes:lib/*", "Enzo.lima.ServidorOnitama", "8080"]

