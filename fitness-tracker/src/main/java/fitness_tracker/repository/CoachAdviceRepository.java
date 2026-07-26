package fitness_tracker.repository;

import fitness_tracker.entity.CoachAdvice;
import fitness_tracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface CoachAdviceRepository extends JpaRepository<CoachAdvice, Long> {
    Optional<CoachAdvice> findByUserAndGeneratedDate(User user, LocalDate generatedDate);
}
