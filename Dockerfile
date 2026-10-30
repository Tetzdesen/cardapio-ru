# A imagem roda a API — consulta, disparo manual, saúde e, com AGENDA_ATIVA=true,
# o anúncio agendado. É o que o Render sobe (render.yaml).

# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# o pom primeiro: assim a camada de dependencias so refaz quando o pom muda
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
# o eclipse-temurin nao traz curl, e sem ele o HEALTHCHECK abaixo reportaria
# "unhealthy" para sempre -- pior que nao ter healthcheck nenhum
RUN apt-get update \
  && apt-get install --no-install-recommends -y curl \
  && rm -rf /var/lib/apt/lists/*
# usuario sem privilegio: a aplicacao nao precisa de root para servir HTTP
RUN useradd --system --create-home --uid 10001 cardapio \
  && mkdir -p /app/estado && chown cardapio /app/estado
# sem o chown, /app/estado seria do root e a gravacao do estado falharia depois
# do envio -- e o anuncio seguinte repetiria tudo
USER cardapio
COPY --from=build /app/target/cardapio-ru-*.jar app.jar

# O estado e um arquivo. Num conteiner sem volume ele some a cada reinicio: monte
# um volume em /app/estado ou defina ESTADO_GITHUB_TOKEN para espelha-lo no GitHub.
VOLUME ["/app/estado"]

# O host injeta PORT; o application.yml ja le essa variavel.
EXPOSE 8080
ENV JAVA_OPTS=""
# sem isto a aplicacao subiria no modo de uma tacada so, nao faria nada e sairia
ENV SPRING_PROFILES_ACTIVE=web

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["sh", "-c", "curl -fsS http://localhost:${PORT:-8080}/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
