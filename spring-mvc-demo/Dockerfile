# syntax=docker/dockerfile:1

# ===== Etap 1: budowanie WAR-a (Maven nie musi być zainstalowany lokalnie) =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Najpierw sam pom.xml: zależności trafiają do osobnej warstwy cache
# i nie pobierają się od nowa przy każdej zmianie w kodzie
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY lombok.config .
COPY src ./src
RUN mvn -B -q package -DskipTests

# ===== Etap 2: obraz uruchomieniowy z Tomcatem 11 =====
FROM tomcat:11.0-jdk21-temurin

# Oficjalny obraz ma pusty webapps/, ale czyścimy go na wszelki wypadek
RUN rm -rf /usr/local/tomcat/webapps/*

# Błędy startu aplikacji (wyjątki Springa) Tomcat domyślnie pisze tylko do pliku
# logs/localhost.<data>.log. Kierujemy je też na konsolę, żeby było je widać
# w "docker logs" / "docker compose logs".
RUN sed -i 's#^org.apache.catalina.core.ContainerBase.\[Catalina\].\[localhost\].handlers = .*#&, java.util.logging.ConsoleHandler#' \
        /usr/local/tomcat/conf/logging.properties

# ROOT.war = aplikacja dostępna pod "/" zamiast "/spring-mvc-demo/"
COPY --from=build /app/target/spring-mvc-demo.war /usr/local/tomcat/webapps/ROOT.war

# Bez zmiennych działa profil dev (H2 w pamięci).
# Profil i dane bazy ustawia się przy starcie kontenera, np.:
#   -e SPRING_PROFILES_ACTIVE=prod -e DB_URL=... -e DB_PASSWORD=...
EXPOSE 8080
CMD ["catalina.sh", "run"]
