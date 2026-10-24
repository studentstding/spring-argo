package pl.app.config;

import java.util.Properties;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Wszystko, co Spring Boot robi przez spring.datasource.*, spring.jpa.*
 * i autokonfigurację, tutaj definiujemy ręcznie: pulę połączeń,
 * EntityManagerFactory (Hibernate), menedżera transakcji i inicjalizację schematu.
 */
@Configuration
@EnableTransactionManagement   // włącza obsługę @Transactional
public class DbConfig {

    /** Pula połączeń HikariCP. Parametry pochodzą z pliku aktywnego profilu. */
    @Bean(destroyMethod = "close")
    public DataSource dataSource(Environment env) {
        HikariConfig cfg = new HikariConfig();
        // Klasę sterownika podajemy jawnie. Na Tomcacie DriverManager nie wykrywa sam
        // sterowników z WEB-INF/lib, więc bez tego start kończy się błędem
        // "Failed to get driver instance for jdbcUrl=...".
        cfg.setDriverClassName(env.getRequiredProperty("db.driver"));
        cfg.setJdbcUrl(env.getRequiredProperty("db.url"));
        cfg.setUsername(env.getRequiredProperty("db.username"));
        cfg.setPassword(env.getProperty("db.password", ""));
        cfg.setMaximumPoolSize(env.getProperty("db.pool-size", Integer.class, 10));
        cfg.setPoolName("app-pool");
        return new HikariDataSource(cfg);
    }

    /** Odpowiednik spring.sql.init.* z Boota: uruchamia schema.sql i data.sql przy starcie. */
    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource, Environment env) {
        var populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/schema.sql"),
                new ClassPathResource("db/data.sql"));
        populator.setSqlScriptEncoding("UTF-8");

        var initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        initializer.setEnabled(env.getProperty("db.init", Boolean.class, false));
        return initializer;
    }

    /**
     * Fabryka EntityManagerów, czyli serce JPA. Bez persistence.xml:
     * encje są wyszukiwane w pakiecie pl.app.model.
     *
     * @DependsOn gwarantuje, że tabele z schema.sql istnieją, zanim
     * Hibernate zacznie walidować schemat (hibernate.hbm2ddl.auto=validate).
     */
    @Bean
    @DependsOn("dataSourceInitializer")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
                                                                       Environment env) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("pl.app.model");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties jpa = new Properties();
        // validate: Hibernate sprawdza, czy encje pasują do tabel, ale ich nie zmienia.
        // Inne wartości: none, update, create, create-drop (te dwie kasują dane).
        jpa.setProperty("hibernate.hbm2ddl.auto", env.getProperty("jpa.ddl-auto", "validate"));
        jpa.setProperty("hibernate.show_sql", env.getProperty("jpa.show-sql", "false"));
        jpa.setProperty("hibernate.format_sql", env.getProperty("jpa.show-sql", "false"));
        // Dialekt (H2, MySQL) Hibernate wykrywa sam na podstawie połączenia.
        emf.setJpaProperties(jpa);
        return emf;
    }

    /** Menedżer transakcji dla JPA. Bez niego @Transactional nie ma czym zarządzać. */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    // Każdy profil wczytuje własny plik z parametrami bazy.

    @Configuration
    @Profile("dev")
    @PropertySource(value = "classpath:db-dev.properties", encoding = "UTF-8")
    static class Dev {
    }

    @Configuration
    @Profile("prod")
    @PropertySource(value = "classpath:db-prod.properties", encoding = "UTF-8")
    static class Prod {
    }
}
