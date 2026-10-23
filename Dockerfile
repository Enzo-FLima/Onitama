# Imagem única para o Render: servidor Spring Boot (porta 8080, interna) + Caddy (porta $PORT, pública)
# servindo o front-end e encaminhando /ws para o Java. O Render fornece o HTTPS.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY Onitama/pom.xml .
RUN mvn -q dependency:go-offline
COPY Onitama/src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=caddy:2-alpine /usr/bin/caddy /usr/bin/caddy
WORKDIR /app
COPY --from=build /app/target/onitama.jar ./onitama.jar
COPY FrontEnd /srv
COPY Caddyfile /etc/caddy/Caddyfile
ENV BACKEND_ADDR=localhost:8080
EXPOSE 10000
CMD ["sh", "-c", "java -XX:MaxRAMPercentage=60 -jar onitama.jar --server.port=8080 & SITE_ADDRESS=\":${PORT:-10000}\" exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile"]
