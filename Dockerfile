FROM eclipse-temurin:21-jre

COPY target/*.jar app.jarl

ENTRYPOINT ["java","-jar","/app.jar"]
