package fitness_tracker.repository;

import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LiftPrRepository extends JpaRepository<LiftPr, Long> {
    List<LiftPr> findByUser(User user);
    Optional<LiftPr> findByUserAndExerciseName(User user, String exerciseName);
}
