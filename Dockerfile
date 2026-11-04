FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Официальный Maven Central (без китайского зеркала)
RUN mkdir -p /root/.m2 && \
    cat > /root/.m2/settings.xml <<'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>maven-central</id>
      <mirrorOf>central</mirrorOf>
      <name>Maven Central</name>
      <url>https://repo.maven.apache.org/maven2</url>
    </mirror>
  </mirrors>
</settings>
EOF

COPY example/pom.xml .
COPY example/src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]