package fitness_tracker.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "<html><body><h1>Hello, world!</h1></body></html>";
    }

    @GetMapping("/hello2")
    public String hello2() {
        return "<html><body><h1>這是HELLO2</h1></body></html>";
    }

    @GetMapping("/hello3")
    public String hello3() {
        return "<html><body><h1>這是HELLO0021</h1></body></html>";
    }
}
