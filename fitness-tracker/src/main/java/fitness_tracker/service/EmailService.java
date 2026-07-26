package fitness_tracker.service;

import fitness_tracker.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// 寄信：有設 SMTP 就寄真信；沒設或失敗就把連結印到 console（開發模式），永不擋流程。
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailProvider;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.username:}")
    private String from;

    public EmailService(ObjectProvider<JavaMailSender> mailProvider) { this.mailProvider = mailProvider; }

    public void sendVerification(User u, String token) {
        String link = baseUrl + "/verify?token=" + token;
        send(u.getEmail(), "FitTracker 帳號驗證", "歡迎加入 FitTracker！請點以下連結啟用帳號（24 小時內有效）：\n" + link);
    }

    public void sendPasswordReset(User u, String token) {
        String link = baseUrl + "/reset?token=" + token;
        send(u.getEmail(), "FitTracker 密碼重設", "請點以下連結重設密碼（1 小時內有效）。若非本人操作請忽略此信：\n" + link);
    }

    private void send(String to, String subject, String body) {
        JavaMailSender sender = mailProvider.getIfAvailable();
        if (sender != null) {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                if (from != null && !from.isBlank()) m.setFrom(from);
                m.setTo(to);
                m.setSubject(subject);
                m.setText(body);
                sender.send(m);
                log.info("已寄信給 {}：{}", to, subject);
                return;
            } catch (Exception e) {
                log.warn("寄信失敗，改印連結到 console：{}", e.getMessage());
            }
        }
        // 開發模式：沒設 SMTP 或寄信失敗 -> 連結印在 console，可直接複製點開
        log.info("\n===== [開發模式·請手動點連結] =====\n收件：{}\n主旨：{}\n{}\n==================================", to, subject, body);
    }
}
