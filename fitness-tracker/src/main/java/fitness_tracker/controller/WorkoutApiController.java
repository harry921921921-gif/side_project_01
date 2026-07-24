package fitness_tracker.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fitness_tracker.dto.WorkoutRequest;
import fitness_tracker.dto.WorkoutResponse;
import fitness_tracker.entity.User;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.entity.WorkoutSet;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.WorkoutService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutApiController {

    private final WorkoutService service;
    private final CurrentUserService currentUserService;

    public WorkoutApiController(WorkoutService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public Page<WorkoutResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findPage(pageable, currentUserService.getCurrentUser()).map(this::toResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkoutResponse> get(@PathVariable long id) {
        WorkoutSession workout = service.findById(id, currentUserService.getCurrentUser())
                .orElseThrow(() -> new fitness_tracker.exception.ResourceNotFoundException("找不到 id=" + id + " 的訓練紀錄"));
        return ResponseEntity.ok(toResponse(workout));
    }

    @PostMapping
    public ResponseEntity<WorkoutResponse> create(@Valid @RequestBody WorkoutRequest req) {
        WorkoutSession session = new WorkoutSession();
        session.setWorkoutDate(req.workoutDate());
        session.setBodyPart(req.bodyPart());
        session.setNote(req.note());

        List<WorkoutRequest.ExerciseDto> exercises = req.exercises() != null ? req.exercises() : List.of();
        User user = currentUserService.getCurrentUser();
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
                exercises.stream().map(WorkoutRequest.ExerciseDto::notes).toList(),
                user
        );
        return ResponseEntity.ok(toResponse(session));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    private WorkoutResponse toResponse(WorkoutSession session) {
        List<WorkoutResponse.WorkoutSetResponse> sets = session.getSets() == null ? List.of() : session.getSets().stream()
                .map(this::toSetResponse)
                .toList();

        return new WorkoutResponse(
                session.getId(),
                session.getWorkoutDate(),
                session.getBodyPart(),
                session.getNote(),
                sets
        );
    }

    private WorkoutResponse.WorkoutSetResponse toSetResponse(WorkoutSet set) {
        return new WorkoutResponse.WorkoutSetResponse(
                set.getId(),
                set.getExerciseName(),
                set.getWeightKg(),
                set.getSets(),
                set.getReps(),
                set.getRestSeconds(),
                set.getRpe(),
                set.getCompletionStatus() != null ? set.getCompletionStatus().name() : null,
                set.getActualReps(),
                set.getActualWeight(),
                set.getNotes()
        );
    }
}
