package fitness_tracker.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 記住我 cookie 之外唯一一道擋暴力破解的防線：同一個「信箱＋來源 IP」組合連續失敗太多次就暫時鎖住。
// 用記憶體 Map 存，重啟服務就清空——對這個規模的個人專案夠用，不需要為此另外接 Redis。
//
// 鎖定用「信箱＋IP」而不是只用信箱：只認信箱的話，任何知道某人信箱的人都能從自己的電腦不斷打錯
// 密碼，把對方的帳號鎖住——本人從自己平常的裝置登入完全不受影響，卻進不去自己的帳號，等於一個
// 免費的騷擾工具。改成信箱＋IP 之後，攻擊者只會鎖住「他自己這個 IP 對這個信箱」的嘗試，本人從
// 自己的裝置登入不會被牽連，但同一個人真的想暴力破解同一個帳號，還是一樣會被擋下來。
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private record Attempts(int count, Instant lockedUntil) {}

    private final Map<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();

    public void loginFailed(String email, String ip) {
        String key = key(email, ip);
        if (key.isEmpty()) return;
        attemptsByKey.compute(key, (k, existing) -> {
            int count = (existing == null ? 0 : existing.count()) + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? Instant.now().plus(LOCKOUT_DURATION) : null;
            return new Attempts(count, lockedUntil);
        });
    }

    public void loginSucceeded(String email, String ip) {
        attemptsByKey.remove(key(email, ip));
    }

    public boolean isLocked(String email, String ip) {
        Attempts a = attemptsByKey.get(key(email, ip));
        if (a == null || a.lockedUntil() == null) return false;
        if (Instant.now().isAfter(a.lockedUntil())) {
            attemptsByKey.remove(key(email, ip));
            return false;
        }
        return true;
    }

    private String key(String email, String ip) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) return "";
        return normalizedEmail + "|" + (ip == null || ip.isBlank() ? "unknown" : ip.trim());
    }
}
