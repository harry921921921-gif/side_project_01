package fitness_tracker.controller;

import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.ExerciseService;
import fitness_tracker.service.LiftPrService;
import fitness_tracker.service.TrainingPlanService;
import fitness_tracker.service.WorkoutPlanService;
import fitness_tracker.service.WorkoutService;
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
    private final WorkoutService workoutService;
    private final WorkoutPlanService workoutPlanService;
    private final ExerciseService exerciseService;

    public PlanController(CurrentUserService currentUserService, TrainingPlanService trainingPlanService,
                          LiftPrService liftPrService, WorkoutService workoutService,
                          WorkoutPlanService workoutPlanService, ExerciseService exerciseService) {
        this.currentUserService = currentUserService;
        this.trainingPlanService = trainingPlanService;
        this.liftPrService = liftPrService;
        this.workoutService = workoutService;
        this.workoutPlanService = workoutPlanService;
        this.exerciseService = exerciseService;
    }

    @GetMapping("/plan")
    public String plan(Model model) {
        User user = currentUserService.getCurrentUser();
        TrainingPlan p = trainingPlanService.getOrCreateForUser(user);
        int week = trainingPlanService.currentWeek(p, LocalDate.now());
        model.addAttribute("planMode", p.getMode().name());
        model.addAttribute("planDays", p.getDaysPerWeek());
        model.addAttribute("planWeek", week);
        Map<String, Object> prs = new HashMap<>();
        for (LiftPr pr : liftPrService.findByUser(user)) {
            prs.put(pr.getExerciseName(), Map.of("w", pr.getWeightKg(), "r", pr.getReps()));
        }
        model.addAttribute("planPrs", prs);
        model.addAttribute("completedDays", workoutService.completedBodyPartsThisWeek(user));
        // 課表卡片組成（哪些主項/配件）現在真的查 Exercise 表選，不再是前端寫死的清單；
        // 帶週次讓配件池依「第幾週＋A/B」旋轉，同一天型態不會週週長一樣；
        // 帶 extraQueueCount 把「新增課表」多排出來、已持久化的張數重建回來，重新登入不會不見
        model.addAttribute("planQueue",
                workoutPlanService.currentQueueComposition(user, p.getDaysPerWeek(), week, p.getExtraQueueCount()));
        // 配件動作可以換成的清單，來源是 Exercise 表（排除四大主項），給卡片編輯面板的下拉選單用
        List<String> accessoryPool = exerciseService.findAll().stream()
                .map(e -> e.getName())
                .filter(name -> !WorkoutPlanService.MAIN_LIFT_NAMES.contains(name))
                .sorted()
                .toList();
        model.addAttribute("accessoryPool", accessoryPool);
        return "plan/index";
    }

    // 存程度/天數/PR——不動 phaseStartDate，週次由伺服器依日曆自動前進，不會因為存別的東西被洗掉
    @PostMapping("/plan/save")
    public String save(@RequestParam String mode,
                       @RequestParam int days,
                       @RequestParam(required = false) List<String> prName,
                       @RequestParam(required = false) List<Double> prWeight,
                       @RequestParam(required = false) List<Integer> prReps) {
        User user = currentUserService.getCurrentUser();
        PlanMode m = "veteran".equalsIgnoreCase(mode) || "VETERAN".equalsIgnoreCase(mode) ? PlanMode.VETERAN : PlanMode.NOVICE;
        int d = Math.min(Math.max(days, 1), 7);
        String csv = defaultWeekdays(d);
        trainingPlanService.saveOrUpdate(user, m, d, csv, null);

        if (prName != null && prWeight != null && prReps != null) {
            for (int i = 0; i < prName.size(); i++) {
                if (i < prWeight.size() && i < prReps.size() && prWeight.get(i) != null && prWeight.get(i) > 0) {
                    liftPrService.save(user, prName.get(i), prWeight.get(i), prReps.get(i) == null ? 1 : prReps.get(i));
                }
            }
        }
        return "redirect:/plan?saved";
    }

    // 手動校正目前第幾週——獨立的動作，不會被「儲存我的課表」意外覆蓋
    @PostMapping("/plan/week")
    public String setWeek(@RequestParam int week) {
        User user = currentUserService.getCurrentUser();
        trainingPlanService.setCurrentWeek(user, week);
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
