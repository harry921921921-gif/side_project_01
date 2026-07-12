package fitness_tracker.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fitness_tracker.dto.ExerciseRequest;
import fitness_tracker.dto.ExerciseResponse;
import fitness_tracker.entity.Exercise;
import fitness_tracker.service.ExerciseService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseApiController {

    private final ExerciseService service;

    public ExerciseApiController(ExerciseService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExerciseResponse> list(
            @RequestParam(required = false) String bodyPart,
            @RequestParam(required = false) String q) {

        List<Exercise> exercises;
        if (q != null && !q.isBlank()) {
            exercises = service.search(q);
        } else if (bodyPart != null && !bodyPart.isBlank()) {
            exercises = service.findByBodyPart(bodyPart);
        } else {
            exercises = service.findAll();
        }
        return exercises.stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<ExerciseResponse> addCustom(@Valid @RequestBody ExerciseRequest req) {
        String name = req.name() != null ? req.name().trim() : null;
        String bodyPart = req.bodyPart() != null ? req.bodyPart().trim() : null;
        String category = req.category() != null ? req.category().trim() : "ISOLATION";

        return service.addCustom(name, bodyPart, category)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(409).build());
    }

    private ExerciseResponse toResponse(Exercise exercise) {
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getBodyPart(),
                exercise.getCategory(),
                exercise.isPreset(),
                exercise.getOrderIndex()
        );
    }
}
