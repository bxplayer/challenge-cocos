# ---------- build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache de dependencias: primero el POM y el wrapper
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline

# Código y empaquetado
COPY src/ src/
RUN ./mvnw -q -B clean package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /app/target/broker-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
