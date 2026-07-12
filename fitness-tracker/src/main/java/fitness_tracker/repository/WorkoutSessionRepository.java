package fitness_tracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import fitness_tracker.entity.WorkoutSession;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    @EntityGraph(attributePaths = "sets")
    List<WorkoutSession> findAllByOrderByWorkoutDateDesc();

    @EntityGraph(attributePaths = "sets")
    Page<WorkoutSession> findAllByOrderByWorkoutDateDesc(Pageable pageable);

    @EntityGraph(attributePaths = "sets")
    Optional<WorkoutSession> findById(Long id);

    @EntityGraph(attributePaths = "sets")
    List<WorkoutSession> findByWorkoutDateBetweenOrderByWorkoutDateDesc(LocalDate startDate, LocalDate endDate);

    long countByWorkoutDateGreaterThanEqual(LocalDate startDate);
}
