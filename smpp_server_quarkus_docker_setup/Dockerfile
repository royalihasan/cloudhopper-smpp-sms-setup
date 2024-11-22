# Stage 1: Build the native executable
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:22.2.0.0-Final-java17 AS build
WORKDIR /app

COPY . .
RUN ./mvnw package -Pnative -Dquarkus.native.container-build=true -DskipTests

# Stage 2: Create the runtime image
FROM registry.access.redhat.com/ubi8/ubi-minimal:latest
WORKDIR /app

COPY --from=build /app/target/*-runner /app/application
EXPOSE 8080 2775
CMD ["./application"]
