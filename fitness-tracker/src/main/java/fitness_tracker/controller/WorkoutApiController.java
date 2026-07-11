package fitness_tracker.controller;

import fitness_tracker.dto.WorkoutRequest;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.service.WorkoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutApiController {

    private final WorkoutService service;

    public WorkoutApiController(WorkoutService service) {
        this.service = service;
    }

    // GET /api/workouts
    @GetMapping
    public List<WorkoutSession> list() {
        return service.findAll();
    }

    // GET /api/workouts/{id}
    @GetMapping("/{id}")
    public ResponseEntity<WorkoutSession> get(@PathVariable long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/workouts
    // Body: { "workoutDate": "2026-06-02", "bodyPart": "Chest", "note": "...",
    //         "exercises": [{ "exerciseName": "Bench Press", "weightKg": 80, "sets": 3, "reps": 10,
    //                         "rpe": 8.5, "completionStatus": "COMPLETE", "actualReps": 10,
    //                         "actualWeight": 80, "notes": "..." }] }
    @PostMapping
    public ResponseEntity<WorkoutSession> create(@RequestBody WorkoutRequest req) {
        WorkoutSession session = new WorkoutSession();
        session.setWorkoutDate(req.workoutDate());
        session.setBodyPart(req.bodyPart());
        session.setNote(req.note());

        List<WorkoutRequest.ExerciseDto> exercises = req.exercises() != null ? req.exercises() : List.of();
        service.save(
                session,
                exercises.stream().map(WorkoutRequest.ExerciseDto::exerciseName).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::weightKg).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::sets).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::reps).toList(),
                exercises.stream().<Integer>map(w -> null).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::rpe).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::completionStatus).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::actualReps).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::actualWeight).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::notes).toList()
        );
        return ResponseEntity.ok(session);
    }

    // DELETE /api/workouts/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
