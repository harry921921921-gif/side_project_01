package fitness_tracker.service;

import fitness_tracker.entity.EmailToken;
import fitness_tracker.entity.User;
import fitness_tracker.repository.EmailTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailTokenService {

    public static final String VERIFY = "VERIFY";
    public static final String RESET = "RESET";

    private final EmailTokenRepository repo;

    public EmailTokenService(EmailTokenRepository repo) { this.repo = repo; }

    @Transactional
    public EmailToken create(User user, String type, long ttlHours) {
        EmailToken t = new EmailToken();
        t.setToken(UUID.randomUUID().toString());
        t.setUser(user);
        t.setType(type);
        t.setExpiresAt(LocalDateTime.now().plusHours(ttlHours));
        t.setUsed(false);
        return repo.save(t);
    }

    @Transactional(readOnly = true)
    public Optional<EmailToken> validate(String token, String type) {
        return repo.findByTokenFetchUser(token)
                .filter(t -> !t.isUsed()
                        && type.equals(t.getType())
                        && t.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Transactional
    public void markUsed(EmailToken t) {
        t.setUsed(true);
        repo.save(t);
    }
}
