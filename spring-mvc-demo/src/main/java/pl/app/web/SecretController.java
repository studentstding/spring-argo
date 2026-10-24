package pl.app.web;

import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SecretController {

    @Value("${db-password}")
    private String dbPassword;

    @GetMapping("/secret")
    public String secret() {
        return "Secret: "
                + dbPassword;
    }
}
