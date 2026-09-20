# A imagem existe para rodar a API — consulta, disparo manual e saúde. Ela não
# participa do agendamento: o anúncio diário roda como execução de uma tacada só
# dentro do próprio GitHub Actions, sem host e sem banco.

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
RUN useradd --system --create-home --uid 10001 cardapio
USER cardapio
COPY --from=build /app/target/cardapio-ru-*.jar app.jar

# O estado e um arquivo. Num conteiner sem volume ele some a cada reinicio, e o
# cardapio do dia seria reanunciado -- a aplicacao avisa isso no log da partida.
# Monte um volume em /app/estado para hospedar de verdade.
VOLUME ["/app/estado"]

# O host injeta PORT; o application.yml ja le essa variavel.
EXPOSE 8080
ENV JAVA_OPTS=""
# sem isto a aplicacao subiria no modo de uma tacada so, nao faria nada e sairia
ENV SPRING_PROFILES_ACTIVE=web

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["sh", "-c", "curl -fsS http://localhost:${PORT:-8080}/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
