package fitness_tracker.controller;

import fitness_tracker.entity.EmailToken;
import fitness_tracker.entity.User;
import fitness_tracker.repository.UserRepository;
import fitness_tracker.service.EmailService;
import fitness_tracker.service.EmailTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final EmailService emailService;
    private final EmailTokenService tokenService;

    @Value("${app.verification.enabled:false}")
    private boolean verificationEnabled;

    public AuthController(UserRepository users, PasswordEncoder encoder,
                          EmailService emailService, EmailTokenService tokenService) {
        this.users = users;
        this.encoder = encoder;
        this.emailService = emailService;
        this.tokenService = tokenService;
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
        u.setPasswordHash(encoder.encode(password));
        u.setEnabled(!verificationEnabled);   // 要驗證就先停用，直到點驗證信
        try {
            users.save(u);
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "這個信箱已經註冊過了");
            return "redirect:/register";
        }
        if (verificationEnabled) {
            EmailToken t = tokenService.create(u, EmailTokenService.VERIFY, 24);
            emailService.sendVerification(u, t.getToken());
            return "redirect:/login?checkEmail";
        }
        return "redirect:/login?registered";
    }

    // 點驗證信連結 -> 啟用帳號
    @GetMapping("/verify")
    public String verify(@RequestParam String token) {
        return tokenService.validate(token, EmailTokenService.VERIFY).map(t -> {
            User u = t.getUser();
            u.setEnabled(true);
            users.save(u);
            tokenService.markUsed(t);
            return "redirect:/login?verified";
        }).orElse("redirect:/login?badtoken");
    }

    // 忘記密碼：輸入信箱
    @GetMapping("/forgot")
    public String forgotForm() { return "auth/forgot"; }

    @PostMapping("/forgot")
    public String forgot(@RequestParam String email) {
        // 不論信箱是否存在都回同樣訊息，避免洩漏哪些信箱有註冊
        if (email != null && !email.isBlank()) {
            users.findByEmail(email.trim()).ifPresent(u -> {
                EmailToken t = tokenService.create(u, EmailTokenService.RESET, 1);
                emailService.sendPasswordReset(u, t.getToken());
            });
        }
        return "redirect:/forgot?sent";
    }

    // 點重設信連結 -> 顯示重設表單
    @GetMapping("/reset")
    public String resetForm(@RequestParam String token, Model model) {
        if (tokenService.validate(token, EmailTokenService.RESET).isEmpty()) {
            return "redirect:/login?badtoken";
        }
        model.addAttribute("token", token);
        return "auth/reset";
    }

    @PostMapping("/reset")
    public String reset(@RequestParam String token,
                        @RequestParam String password,
                        RedirectAttributes ra) {
        if (password == null || password.length() < 8) {
            ra.addFlashAttribute("error", "密碼至少 8 碼");
            return "redirect:/reset?token=" + token;
        }
        return tokenService.validate(token, EmailTokenService.RESET).map(t -> {
            User u = t.getUser();
            u.setPasswordHash(encoder.encode(password));
            users.save(u);
            tokenService.markUsed(t);
            return "redirect:/login?reset";
        }).orElse("redirect:/login?badtoken");
    }
}
