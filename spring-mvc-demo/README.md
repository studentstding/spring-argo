# spring-mvc-demo

Minimalna aplikacja Spring MVC bez Spring Boota (Spring Framework 7, Java 17+),
z bazą danych przez JPA (Hibernate 7) i zapytania JPQL oraz Lombokiem.

## Struktura

```
pom.xml                        zależności + BOM Springa, Lombok, pakowanie WAR
lombok.config                  ustawienia Lomboka (kopiowanie @Value do konstruktora)
src/main/resources/
  app.properties               własne właściwości aplikacji
  db-dev.properties            baza dla profilu dev (H2 w pamięci, tryb MySQL)
  db-prod.properties           baza dla profilu prod (MySQL, zmienne środowiskowe)
  db/schema.sql, db/data.sql   schemat i dane startowe
src/main/java/pl/app/
  config/WebInit.java          zamiennik web.xml, ustawia domyślny profil "dev"
  config/AppConfig.java        kontekst root, importuje DbConfig
  config/DbConfig.java         DataSource (HikariCP), EntityManagerFactory (Hibernate),
                               JpaTransactionManager, init schematu
  config/WebConfig.java        @EnableWebMvc, kontrolery
  model/Note.java              encja JPA (@Entity, @Getter, @NoArgsConstructor)
  repository/NoteRepository    EntityManager + zapytania JPQL
  service/NoteService          @Transactional
  web/NoteController           REST: /notes
  web/HelloController          GET /hello
```

## Uruchomienie

1. `mvn package`
2. Skopiuj `target/spring-mvc-demo.war` do `webapps/` w Tomcat 11
   (Spring 7 wymaga Servlet 6.1).
3. Uruchom Tomcata. Domyślnie działa profil **dev** z bazą H2 w pamięci.

### Endpointy

```
GET    http://localhost:8080/spring-mvc-demo/notes
GET    http://localhost:8080/spring-mvc-demo/notes/1
GET    http://localhost:8080/spring-mvc-demo/notes/search?q=spring   (JPQL z LIKE)
POST   http://localhost:8080/spring-mvc-demo/notes     body: {"text":"Nowa notatka"}
PUT    http://localhost:8080/spring-mvc-demo/notes/1   body: {"text":"Zmieniona"}
DELETE http://localhost:8080/spring-mvc-demo/notes/1
GET    http://localhost:8080/spring-mvc-demo/hello?name=Ania
```

Przykład z curl:
```
curl -X POST -H "Content-Type: application/json" \
     -d '{"text":"Nowa notatka"}' \
     http://localhost:8080/spring-mvc-demo/notes
```

### Profil prod (MySQL)

Załóż bazę i tabelę (skrypt `db/schema.sql`), potem ustaw zmienne i profil
w `bin/setenv.sh` Tomcata (`setenv.bat` na Windows):

```
export DB_URL=jdbc:mysql://localhost:3306/appdb
export DB_USER=app
export DB_PASSWORD=tajne
export CATALINA_OPTS="-Dspring.profiles.active=prod"
```

W obu profilach Hibernate ma `hbm2ddl.auto=validate`: przy starcie sprawdza,
czy encje pasują do tabel, ale niczego w bazie nie zmienia. W dev widać
generowany SQL w logach (`jpa.show-sql=true`).

W prod `db.init=false`, więc aplikacja nie rusza schematu. Na produkcji
schemat lepiej zakładać migracjami (Flyway lub Liquibase).

## Lombok

Lombok generuje kod w czasie kompilacji:
- `@RequiredArgsConstructor` w serwisach i kontrolerach: konstruktor dla pól `final`,
  czyli wstrzykiwanie zależności bez ręcznie pisanego konstruktora,
- `@Getter`, `@Setter`, `@NoArgsConstructor` w encji `Note`.

W encji celowo nie ma `@Data`, bo jego `equals`/`hashCode` po wszystkich polach
źle działa z encjami JPA.

W IntelliJ zainstaluj wtyczkę Lombok (w nowszych wersjach jest wbudowana)
i włącz *Settings → Build → Compiler → Annotation Processors → Enable annotation processing*.

## Docker

Do zbudowania obrazu wystarczy Docker. Maven i JDK nie są potrzebne lokalnie,
bo Dockerfile buduje WAR w osobnym etapie.

W obrazie aplikacja jest wdrożona jako `ROOT.war`, więc adresy są bez
`/spring-mvc-demo`, np. `http://localhost:8080/notes`.

### Sam obraz (profil dev, H2 w pamięci)

```
docker build -t spring-mvc-demo:1.0.0 .
docker run --rm -p 8080:8080 spring-mvc-demo:1.0.0
curl http://localhost:8080/notes
```

### Aplikacja + MySQL (profil prod)

```
docker compose up --build
curl http://localhost:8080/notes
```

Hasło do bazy można podać przez zmienną: `DB_PASSWORD=mojehaslo docker compose up --build`
(domyślnie `tajne`). Schemat i dane startowe zakłada MySQL przy pierwszym
uruchomieniu. Żeby zacząć od pustej bazy: `docker compose down -v`.

Logi Tomcata i Springa: `docker compose logs -f app`.

## Co tu robi się ręcznie, a co Boot zrobiłby sam

| Ręcznie tutaj | W Spring Boot |
|---|---|
| `WebInit` rejestruje DispatcherServlet | autokonfiguracja |
| `@EnableWebMvc` w `WebConfig` | autokonfiguracja |
| `@PropertySource` dla każdego pliku | `application.properties` + `application-{profil}.properties` ładowane same |
| bean `HikariDataSource` z parametrami z `Environment` | `spring.datasource.url/username/password` |
| bean `LocalContainerEntityManagerFactoryBean` + `HibernateJpaVendorAdapter` | autokonfiguracja ze `spring-boot-starter-data-jpa` |
| `setPackagesToScan("pl.app.model")` | skanowanie encji od pakietu głównej klasy |
| właściwości `hibernate.*` w `Properties` | `spring.jpa.hibernate.ddl-auto`, `spring.jpa.show-sql` |
| `@EnableTransactionManagement` + `JpaTransactionManager` | autokonfiguracja |
| repozytorium z `EntityManager` i JPQL | interfejs `JpaRepository` ze Spring Data |
| `DataSourceInitializer` z `schema.sql`/`data.sql` | `spring.sql.init.mode` |
| zewnętrzny Tomcat + WAR | wbudowany Tomcat, `java -jar` |
