package fitness_tracker.repository;

import fitness_tracker.entity.EmailToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {
    Optional<EmailToken> findByToken(String token);

    // join fetch user：呼叫端（AuthController）會在 validate() 的交易結束後才存取 t.getUser()，
    // user 是 LAZY 關聯，不 fetch join 的話會噴 LazyInitializationException
    @Query("select t from EmailToken t join fetch t.user where t.token = :token")
    Optional<EmailToken> findByTokenFetchUser(String token);
}
