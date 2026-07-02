package fitness_tracker.repository;

import fitness_tracker.entity.BodyPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BodyPartRepository extends JpaRepository<BodyPart, Long> {
    List<BodyPart> findAllByOrderByOrderIndexAsc();
    boolean existsByName(String name);
    Optional<BodyPart> findByName(String name);
}
