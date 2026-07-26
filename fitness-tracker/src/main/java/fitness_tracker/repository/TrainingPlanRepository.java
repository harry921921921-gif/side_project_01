package fitness_tracker.repository;

import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {
    Optional<TrainingPlan> findByUser(User user);
}
