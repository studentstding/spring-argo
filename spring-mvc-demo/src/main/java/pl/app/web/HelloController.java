package pl.app.web;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.app.service.GreetingService;

@RestController
@RequiredArgsConstructor
public class HelloController {

    private final GreetingService service;

    @GetMapping("/hello")
    public Map<String, String> hello(@RequestParam(defaultValue = "świecie") String name) {
        return Map.of("message", service.greet(name));
    }
}
