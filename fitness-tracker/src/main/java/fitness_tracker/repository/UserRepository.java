package fitness_tracker.repository;

import fitness_tracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // 第一位註冊的使用者：舊資料（user_id=NULL）遷移時指派給他
    Optional<User> findFirstByOrderByIdAsc();
}
