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
import fitness_tracker.entity.User;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.ExerciseService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseApiController {

    private final ExerciseService service;
    private final CurrentUserService currentUserService;

    public ExerciseApiController(ExerciseService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
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

    // 一般登入使用者（不用 ADMIN）在訓練紀錄/課表編輯彈窗打字「＋新增動作」時呼叫——建立的是只有
    // 自己看得到的個人自訂動作，不會動到 /manage 那邊全站共用的目錄，所以不用 hasRole("ADMIN")。
    // 路徑跟上面 POST /api/exercises 不同，SecurityConfig 對 ADMIN 的限制只精準匹配 /api/exercises
    // 這個路徑本身，不含子路徑，所以 /api/exercises/mine 自然落在一般「登入即可用」的規則裡
    @PostMapping("/mine")
    public ResponseEntity<ExerciseResponse> addPersonal(@Valid @RequestBody ExerciseRequest req) {
        User user = currentUserService.getCurrentUser();
        String name = req.name() != null ? req.name().trim() : null;
        String bodyPart = req.bodyPart() != null ? req.bodyPart().trim() : null;
        String category = req.category() != null ? req.category().trim() : null;

        return service.addPersonal(user, name, bodyPart, category)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
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
