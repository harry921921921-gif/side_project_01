package fitness_tracker.service;

import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class WorkoutService {

    private final WorkoutSessionRepository repository;

    public WorkoutService(WorkoutSessionRepository repository) {
        this.repository = repository;
    }

    public Optional<WorkoutSession> findById(long id) {
        return repository.findById(id);
    }

    public List<WorkoutSession> findAll() {
        return repository.findAllByOrderByWorkoutDateDesc();
    }

    public List<WorkoutSession> findRecent(int limit) {
        List<WorkoutSession> all = findAll();
        return all.subList(0, Math.min(limit, all.size()));
    }

    public long countThisWeek() {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        return repository.countByWorkoutDateGreaterThanEqual(monday);
    }

    @Transactional
    public void save(WorkoutSession session,
                     List<String> exerciseNames,
                     List<Double> weightKgs,
                     List<Integer> sets,
                     List<Integer> reps,
                     List<Integer> restSeconds) {

        for (int i = 0; i < exerciseNames.size(); i++) {
            String name = exerciseNames.get(i);
            if (name != null && !name.trim().isEmpty()) {
                WorkoutSet workoutSet = new WorkoutSet();
                workoutSet.setExerciseName(name.trim());
                workoutSet.setWeightKg(safeGet(weightKgs, i));
                workoutSet.setSets(safeGet(sets, i));
                workoutSet.setReps(safeGet(reps, i));
                workoutSet.setRestSeconds(safeGet(restSeconds, i));
                workoutSet.setSession(session);
                session.getSets().add(workoutSet);
            }
        }
        repository.save(session);
    }

    @Transactional
    public void update(Long id,
                       LocalDate workoutDate,
                       String bodyPart,
                       String note,
                       List<String> exerciseNames,
                       List<Double> weightKgs,
                       List<Integer> sets,
                       List<Integer> reps,
                       List<Integer> restSeconds) {

        WorkoutSession existing = repository.findById(id).orElseThrow();
        existing.setWorkoutDate(workoutDate);
        existing.setBodyPart(bodyPart);
        existing.setNote(note);
        existing.getSets().clear();

        if (exerciseNames != null) {
            for (int i = 0; i < exerciseNames.size(); i++) {
                String name = exerciseNames.get(i);
                if (name != null && !name.trim().isEmpty()) {
                    WorkoutSet ws = new WorkoutSet();
                    ws.setExerciseName(name.trim());
                    ws.setWeightKg(safeGet(weightKgs, i));
                    ws.setSets(safeGet(sets, i));
                    ws.setReps(safeGet(reps, i));
                    ws.setRestSeconds(safeGet(restSeconds, i));
                    ws.setSession(existing);
                    existing.getSets().add(ws);
                }
            }
        }
        repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private <T> T safeGet(List<T> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }
}
