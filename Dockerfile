FROM --platform=$BUILDPLATFORM node:24-alpine AS build_web

COPY src/main/web /webui

WORKDIR /webui

RUN npm install --legacy-peer-deps
RUN npm run build

FROM --platform=$BUILDPLATFORM docker.io/eclipse-temurin:21.0.10_7-jdk-ubi10-minimal AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

RUN ./mvnw dependency:go-offline -B

COPY src src
COPY --from=build_web /webui/dist src/main/resources/static

RUN ./mvnw package -DskipTests -B

FROM docker.io/eclipse-temurin:21.0.10_7-jdk-ubi10-minimal

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m"

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
