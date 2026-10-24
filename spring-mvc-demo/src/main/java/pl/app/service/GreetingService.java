package pl.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GreetingService {

    // lombok.config kopiuje @Value na parametr wygenerowanego konstruktora
    @Value("${app.greeting}")
    private final String greeting;

    public String greet(String name) {
        return greeting + " Witaj, " + name + ".";
    }
}
