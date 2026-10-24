package pl.app.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

/**
 * Zastępuje web.xml. Serwer (Servlet 3.0+) wykrywa tę klasę przy starcie
 * i rejestruje DispatcherServlet.
 */
public class WebInit extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        // Profil domyślny, gdy nikt nie poda -Dspring.profiles.active=...
        servletContext.setInitParameter("spring.profiles.default", "dev");
        super.onStartup(servletContext);
    }

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[]{AppConfig.class};
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[]{WebConfig.class};
    }

    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }
}
