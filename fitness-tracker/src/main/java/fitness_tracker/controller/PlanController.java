package fitness_tracker.controller;

import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.TrainingPlanService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
public class PlanController {

    private final CurrentUserService currentUserService;
    private final TrainingPlanService trainingPlanService;

    public PlanController(CurrentUserService currentUserService, TrainingPlanService trainingPlanService) {
        this.currentUserService = currentUserService;
        this.trainingPlanService = trainingPlanService;
    }

    // 開啟訓練計劃頁：載入使用者已存的課表，帶進 model 供頁面預填
    @GetMapping("/plan")
    public String plan(Model model) {
        User user = currentUserService.getCurrentUser();
        TrainingPlan p = trainingPlanService.getOrCreateForUser(user);
        model.addAttribute("planMode", p.getMode().name());
        model.addAttribute("planDays", p.getDaysPerWeek());
        model.addAttribute("planWeekdays", p.getTrainingWeekdays());
        model.addAttribute("planWeek", trainingPlanService.currentWeek(p, LocalDate.now()));
        return "plan/index";
    }

    // 儲存課表：練哪幾天(勾選) → 一週幾練 = 勾選天數；目前第幾週反推起算日
    @PostMapping("/plan/save")
    public String save(@RequestParam String mode,
                       @RequestParam(required = false) List<String> weekdays,
                       @RequestParam(defaultValue = "1") int week) {
        User user = currentUserService.getCurrentUser();
        if (weekdays == null || weekdays.isEmpty()) {
            return "redirect:/plan?nodays";
        }
        PlanMode m = "VETERAN".equalsIgnoreCase(mode) ? PlanMode.VETERAN : PlanMode.NOVICE;
        String csv = String.join(",", weekdays);
        int daysPerWeek = weekdays.size();
        int clampedWeek = Math.min(Math.max(week, 1), 104);
        LocalDate start = LocalDate.now().minusWeeks(clampedWeek - 1L);
        trainingPlanService.saveOrUpdate(user, m, daysPerWeek, csv, start);
        return "redirect:/plan?saved";
    }
}
