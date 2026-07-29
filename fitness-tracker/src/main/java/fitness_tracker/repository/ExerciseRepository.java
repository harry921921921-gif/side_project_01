package fitness_tracker.repository;

import fitness_tracker.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    Optional<Exercise> findByName(String name);

    List<Exercise> findAllByOrderByBodyPartAscOrderIndexAscNameAsc();

    List<Exercise> findByBodyPartOrderByOrderIndexAscNameAsc(String bodyPart);

    List<Exercise> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    boolean existsByName(String name);

    List<Exercise> findByBodyPartOrderByOrderIndexDesc(String bodyPart);

    // ── 排課用 ──
    List<Exercise> findByMovementIsNull();
    List<Exercise> findByMovementAndCategoryOrderByOrderIndexAscNameAsc(String movement, String category);
    List<Exercise> findByBodyPartAndCategoryOrderByOrderIndexAscNameAsc(String bodyPart, String category);
}
