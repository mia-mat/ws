FROM gradle:8.14-jdk21 AS build

WORKDIR /app


# for caching
COPY gradlew .
RUN chmod +x gradlew
COPY gradle ./gradle
COPY build.gradle ./
COPY settings.gradle ./

RUN ./gradlew --no-daemon build -x test --refresh-dependencies || true

COPY src ./src

RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

# Caddy reverse proxy labels
LABEL caddy=mia.ws
LABEL caddy.reverse_proxy="{{upstreams 8080}}"

COPY --from=build /app/build/libs/*.jar ./app.jar

COPY src/main/resources/initial-fs ./initial-fs


EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]