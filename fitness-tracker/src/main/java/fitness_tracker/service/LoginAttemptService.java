package fitness_tracker.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 記住我 cookie 之外唯一一道擋暴力破解的防線：同一個信箱連續失敗太多次就暫時鎖住。
// 用記憶體 Map 存，重啟服務就清空——對這個規模的個人專案夠用，不需要為此另外接 Redis。
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private record Attempts(int count, Instant lockedUntil) {}

    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();

    public void loginFailed(String email) {
        String key = normalize(email);
        if (key.isEmpty()) return;
        attemptsByEmail.compute(key, (k, existing) -> {
            int count = (existing == null ? 0 : existing.count()) + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? Instant.now().plus(LOCKOUT_DURATION) : null;
            return new Attempts(count, lockedUntil);
        });
    }

    public void loginSucceeded(String email) {
        attemptsByEmail.remove(normalize(email));
    }

    public boolean isLocked(String email) {
        Attempts a = attemptsByEmail.get(normalize(email));
        if (a == null || a.lockedUntil() == null) return false;
        if (Instant.now().isAfter(a.lockedUntil())) {
            attemptsByEmail.remove(normalize(email));
            return false;
        }
        return true;
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
