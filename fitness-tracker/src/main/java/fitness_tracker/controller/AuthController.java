package fitness_tracker.controller;

import fitness_tracker.entity.User;
import fitness_tracker.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/login")
    public String login() { return "auth/login"; }

    @GetMapping("/register")
    public String registerForm() { return "auth/register"; }

    @PostMapping("/register")
    public String register(@RequestParam String displayName,
                           @RequestParam String email,
                           @RequestParam String password,
                           RedirectAttributes ra) {
        if (email == null || email.isBlank() || password == null || password.length() < 8) {
            ra.addFlashAttribute("error", "請填寫完整，密碼至少 8 碼");
            return "redirect:/register";
        }
        String normalizedEmail = email.trim();
        if (users.existsByEmail(normalizedEmail)) {
            ra.addFlashAttribute("error", "這個信箱已經註冊過了");
            return "redirect:/register";
        }
        User u = new User();
        u.setEmail(normalizedEmail);
        u.setDisplayName(displayName == null || displayName.isBlank() ? normalizedEmail : displayName.trim());
        u.setPasswordHash(encoder.encode(password));   // 雜湊後才存
        u.setEnabled(true);
        try {
            users.save(u);
        } catch (DataIntegrityViolationException e) {
            // 併發下兩個請求同時通過 existsByEmail 檢查時的保護
            ra.addFlashAttribute("error", "這個信箱已經註冊過了");
            return "redirect:/register";
        }
        return "redirect:/login?registered";
    }
}
