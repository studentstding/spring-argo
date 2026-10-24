package pl.app.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

/**
 * Kontekst "root": serwisy, repozytoria, baza, właściwości.
 * W Boocie plik application.properties ładuje się sam, tu trzeba go wskazać.
 */
@Configuration
@Import(DbConfig.class)
@ComponentScan({"pl.app.service", "pl.app.repository"})
@PropertySource(value = "classpath:app.properties", encoding = "UTF-8")
@PropertySource(value = "file:/etc/app-secrets/db-secret.properties", encoding = "UTF-8")class AppConfig {
}
