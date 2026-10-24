package pl.app.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Kontekst webowy: kontrolery i ustawienia MVC.
 * @EnableWebMvc włącza to, co w Boocie robi autokonfiguracja
 * (mapowanie requestów, konwertery JSON itd.).
 */
@Configuration
@EnableWebMvc
@ComponentScan("pl.app.web")
public class WebConfig implements WebMvcConfigurer {
}
