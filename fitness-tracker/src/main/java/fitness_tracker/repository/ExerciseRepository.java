package fitness_tracker.repository;

import fitness_tracker.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findAllByOrderByBodyPartAscOrderIndexAscNameAsc();

    List<Exercise> findByBodyPartOrderByOrderIndexAscNameAsc(String bodyPart);

    List<Exercise> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    boolean existsByName(String name);

    List<Exercise> findByBodyPartOrderByOrderIndexDesc(String bodyPart);
}
