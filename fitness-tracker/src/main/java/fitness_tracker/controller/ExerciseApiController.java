package fitness_tracker.controller;

import fitness_tracker.entity.Exercise;
import fitness_tracker.service.ExerciseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseApiController {

    private final ExerciseService service;

    public ExerciseApiController(ExerciseService service) {
        this.service = service;
    }

    // GET /api/exercises
    // GET /api/exercises?bodyPart=胸
    // GET /api/exercises?q=臥推
    @GetMapping
    public List<Exercise> list(
            @RequestParam(required = false) String bodyPart,
            @RequestParam(required = false) String q) {

        if (q != null && !q.isBlank()) {
            return service.search(q);
        }
        if (bodyPart != null && !bodyPart.isBlank()) {
            return service.findByBodyPart(bodyPart);
        }
        return service.findAll();
    }

    // POST /api/exercises
    // Body: { "name": "自訂動作", "bodyPart": "胸", "category": "ISOLATION" }
    @PostMapping
    public ResponseEntity<?> addCustom(@RequestBody Map<String, String> body) {
        String name     = body.get("name");
        String bodyPart = body.get("bodyPart");
        String category = body.getOrDefault("category", "ISOLATION");

        if (name == null || name.isBlank() || bodyPart == null || bodyPart.isBlank()) {
            return ResponseEntity.badRequest().body("name 和 bodyPart 為必填");
        }

        return service.addCustom(name.trim(), bodyPart.trim(), category.trim())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(409).build()); // 409 Conflict = 名稱已存在
    }
}
