package fitness_tracker.controller;

import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.TrainingPlanService;
import fitness_tracker.service.TrainingPlanService.CardOverride;
import fitness_tracker.service.WorkoutPlanService;
import fitness_tracker.service.WorkoutPlanService.DayComposition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// /plan 頁的課表卡片改成即時打這裡拿「動作組成」（哪些主項/配件、排哪一天）——真的查 Exercise 表選動作，
// 不再是前端寫死的清單。重量/組數/次數還是前端自己算（跟頁面既有的「打 PR 馬上看到建議重量」共用同一套公式），
// 這裡只回答「排哪些動作」，天數以外的切換（程度/減量）都不需要打這支。
@RestController
@RequestMapping("/plan/api")
public class PlanApiController {

    private final CurrentUserService currentUserService;
    private final WorkoutPlanService workoutPlanService;
    private final TrainingPlanService trainingPlanService;

    public PlanApiController(CurrentUserService currentUserService, WorkoutPlanService workoutPlanService,
                             TrainingPlanService trainingPlanService) {
        this.currentUserService = currentUserService;
        this.workoutPlanService = workoutPlanService;
        this.trainingPlanService = trainingPlanService;
    }

    // week 選填：帶了就用「週次＋A/B」旋轉配件池，讓同一天型態週與週、A跟B不一樣；不帶就退回舊的固定順序。
    // 一併把使用者用「新增課表」多排出來、已持久化的張數（TrainingPlan.extraQueueCount）接回去，
    // 不然重新整理/重新登入時「本週完整課表」會縮回只剩基本張數
    @GetMapping("/queue")
    public List<DayComposition> queue(@RequestParam int days, @RequestParam(required = false) Integer week) {
        User user = currentUserService.getCurrentUser();
        List<DayComposition> result;
        if (week == null) {
            result = workoutPlanService.currentQueueComposition(user, days);
        } else {
            TrainingPlan p = trainingPlanService.getOrCreateForUser(user);
            result = workoutPlanService.currentQueueComposition(user, days, week, p.getExtraQueueCount());
        }
        return trainingPlanService.applyOverrides(trainingPlanService.getCardOverrides(user), result);
    }

    // extra 選填：分化天數少（如3天）時「新增課表」可能在同一週把分化繞回第二圈，此時同一天名
    // （沒有 A/B 可分）光靠 week 轉不出差異，前端帶「目前佇列已經有幾張卡」進來疊加旋轉量避免撞列。
    // 每呼叫一次代表使用者真的按了一次「新增課表」，把持久化的張數 +1，重新登入才會保留這張卡。
    @GetMapping("/next")
    public DayComposition next(@RequestParam String lastDay, @RequestParam int days,
                               @RequestParam(required = false) Integer week,
                               @RequestParam(required = false, defaultValue = "0") int extra) {
        User user = currentUserService.getCurrentUser();
        DayComposition dc;
        if (week == null) {
            dc = workoutPlanService.nextCompositionInCycle(user, lastDay, days);
        } else {
            dc = workoutPlanService.nextCompositionInCycle(user, lastDay, days, week, extra);
            trainingPlanService.incrementExtraQueueCount(user);
        }
        Map<String, CardOverride> overrides = trainingPlanService.getCardOverrides(user);
        CardOverride ov = overrides.get(dc.dayName());
        return ov == null ? dc : new DayComposition(dc.dayName(), ov.main(), ov.acc());
    }
}
