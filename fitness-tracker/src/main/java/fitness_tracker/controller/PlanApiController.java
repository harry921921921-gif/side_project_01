package fitness_tracker.controller;

import fitness_tracker.entity.User;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.WorkoutPlanService;
import fitness_tracker.service.WorkoutPlanService.DayComposition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// /plan 頁的課表卡片改成即時打這裡拿「動作組成」（哪些主項/配件、排哪一天）——真的查 Exercise 表選動作，
// 不再是前端寫死的清單。重量/組數/次數還是前端自己算（跟頁面既有的「打 PR 馬上看到建議重量」共用同一套公式），
// 這裡只回答「排哪些動作」，天數以外的切換（程度/週次/減量）都不需要打這支。
@RestController
@RequestMapping("/plan/api")
public class PlanApiController {

    private final CurrentUserService currentUserService;
    private final WorkoutPlanService workoutPlanService;

    public PlanApiController(CurrentUserService currentUserService, WorkoutPlanService workoutPlanService) {
        this.currentUserService = currentUserService;
        this.workoutPlanService = workoutPlanService;
    }

    @GetMapping("/queue")
    public List<DayComposition> queue(@RequestParam int days) {
        User user = currentUserService.getCurrentUser();
        return workoutPlanService.currentQueueComposition(user, days);
    }

    @GetMapping("/next")
    public DayComposition next(@RequestParam String lastDay, @RequestParam int days) {
        User user = currentUserService.getCurrentUser();
        return workoutPlanService.nextCompositionInCycle(user, lastDay, days);
    }
}
