FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build

RUN apt-get update && apt-get install -y nodejs npm

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src

WORKDIR /app/src/main/web

RUN npm install --legacy-peer-deps
RUN npm run build

WORKDIR /app

RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m"

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
