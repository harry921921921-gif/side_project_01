package fitness_tracker.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Hello, world!");
    }

    @GetMapping("/hello2")
    public Map<String, String> hello2() {
        return Map.of("message", "這是HELLO2");
    }

        @GetMapping("/hello3")
    public Map<String, String> hello3() {
        return Map.of("message", "這是HELLO3");
    }
}
