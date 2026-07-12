package fitness_tracker.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fitness_tracker.dto.WorkoutRequest;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.service.WorkoutService;
import jakarta.validation.Valid;

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
    public ResponseEntity<WorkoutSession> create(@Valid @RequestBody WorkoutRequest req) {
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
                exercises.stream().map(e -> e.completionStatus() != null ? e.completionStatus().name() : null).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::actualReps).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::actualWeight).toList(),
                exercises.stream().map(WorkoutRequest.ExerciseDto::notes).toList()
        );
        return ResponseEntity.ok(session);
    }

    // DELETE /api/workouts/{id}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
