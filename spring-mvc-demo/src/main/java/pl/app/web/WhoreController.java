package pl.app.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.app.model.Person;
import pl.app.repository.PersonRepository;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WhoreController {

 
    @GetMapping("/whore")
    public String findAll() {
        return "Natalia Czerniszew to kurwa";
    }
}
