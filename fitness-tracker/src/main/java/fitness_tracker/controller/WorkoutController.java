package fitness_tracker.controller;

import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.service.WorkoutService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/workout")
public class WorkoutController {

    private final WorkoutService service;

    public WorkoutController(WorkoutService service) {
        this.service = service;
    }

    // GET /workout → 顯示訓練紀錄頁面
    @GetMapping
    public String index(Model model) {
        model.addAttribute("sessions", service.findAll());
        return "workout/index";  // → templates/workout/index.html
    }

    // POST /workout → 儲存新訓練（包含多個動作）
    @PostMapping
    public String save(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workoutDate,
            @RequestParam String bodyPart,
            @RequestParam(required = false) String note,
            // 多個動作用 List 接收（HTML 表單裡多個同名 input 會自動組成陣列）
            @RequestParam(required = false) List<String> exerciseNames,
            @RequestParam(required = false) List<Double> weightKgs,
            @RequestParam(required = false) List<Integer> sets,
            @RequestParam(required = false) List<Integer> reps) {

        WorkoutSession session = new WorkoutSession();
        session.setWorkoutDate(workoutDate);
        session.setBodyPart(bodyPart);
        session.setNote(note);

        service.save(session,
                exerciseNames != null ? exerciseNames : List.of(),
                weightKgs,
                sets,
                reps);

        return "redirect:/workout";
    }

    // POST /workout/delete/{id} → 刪除整筆訓練（連帶刪除底下所有動作）
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/workout";
    }
}
