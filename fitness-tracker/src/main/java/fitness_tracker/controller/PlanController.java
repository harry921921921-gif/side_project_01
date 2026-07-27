package fitness_tracker.controller;

import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.LiftPrService;
import fitness_tracker.service.TrainingPlanService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PlanController {

    private final CurrentUserService currentUserService;
    private final TrainingPlanService trainingPlanService;
    private final LiftPrService liftPrService;

    public PlanController(CurrentUserService currentUserService, TrainingPlanService trainingPlanService, LiftPrService liftPrService) {
        this.currentUserService = currentUserService;
        this.trainingPlanService = trainingPlanService;
        this.liftPrService = liftPrService;
    }

    @GetMapping("/plan")
    public String plan(Model model) {
        User user = currentUserService.getCurrentUser();
        TrainingPlan p = trainingPlanService.getOrCreateForUser(user);
        model.addAttribute("planMode", p.getMode().name());
        model.addAttribute("planDays", p.getDaysPerWeek());
        model.addAttribute("planWeek", trainingPlanService.currentWeek(p, LocalDate.now()));
        Map<String, Object> prs = new HashMap<>();
        for (LiftPr pr : liftPrService.findByUser(user)) {
            prs.put(pr.getExerciseName(), Map.of("w", pr.getWeightKg(), "r", pr.getReps()));
        }
        model.addAttribute("planPrs", prs);
        return "plan/index";
    }

    @PostMapping("/plan/save")
    public String save(@RequestParam String mode,
                       @RequestParam int days,
                       @RequestParam(defaultValue = "1") int week,
                       @RequestParam(required = false) List<String> prName,
                       @RequestParam(required = false) List<Double> prWeight,
                       @RequestParam(required = false) List<Integer> prReps) {
        User user = currentUserService.getCurrentUser();
        PlanMode m = "veteran".equalsIgnoreCase(mode) || "VETERAN".equalsIgnoreCase(mode) ? PlanMode.VETERAN : PlanMode.NOVICE;
        int d = Math.min(Math.max(days, 1), 7);
        String csv = defaultWeekdays(d);
        LocalDate start = LocalDate.now().minusWeeks(Math.max(week - 1, 0));
        trainingPlanService.saveOrUpdate(user, m, d, csv, start);

        if (prName != null && prWeight != null && prReps != null) {
            for (int i = 0; i < prName.size(); i++) {
                if (i < prWeight.size() && i < prReps.size() && prWeight.get(i) != null && prWeight.get(i) > 0) {
                    liftPrService.save(user, prName.get(i), prWeight.get(i), prReps.get(i) == null ? 1 : prReps.get(i));
                }
            }
        }
        return "redirect:/plan?saved";
    }

    // 依一週天數自動配預設訓練日（使用者不用自己挑星期）
    private static String defaultWeekdays(int days) {
        String[][] d = {
                {},
                {"MONDAY"},
                {"MONDAY", "THURSDAY"},
                {"MONDAY", "WEDNESDAY", "FRIDAY"},
                {"MONDAY", "TUESDAY", "THURSDAY", "FRIDAY"},
                {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"},
                {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"},
                {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"}
        };
        return String.join(",", d[Math.min(Math.max(days, 1), 7)]);
    }
}
