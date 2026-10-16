package fitness_tracker.controller;

import fitness_tracker.entity.LiftPr;
import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.ExerciseService;
import fitness_tracker.service.LiftPrService;
import fitness_tracker.service.PhaseCalendar;
import fitness_tracker.service.TrainingPlanService;
import fitness_tracker.service.WorkoutPlanService;
import fitness_tracker.service.WorkoutService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
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
        // currentWeek() 回的是「開始這個計畫後過了幾週」，會隨時間一直長大、沒有上限；
        // 20 週一輪跑完要折回第 1 週重新開始（見 PhaseCalendar.cycleWeek），不然練超過 20 週
        // 的使用者畫面會一直卡在「第 20 週·最大力量期」，週次數字也會凍結在 20 不會再變
        int week = PhaseCalendar.cycleWeek(trainingPlanService.currentWeek(p, LocalDate.now()));
        model.addAttribute("planMode", p.getMode().name());
        model.addAttribute("planDays", p.getDaysPerWeek());
        model.addAttribute("planWeek", week);
        // 給多裝置/多分頁編輯衝突偵測用：前端編輯課表卡片時把這個值原封不動存起來，
        // 存檔時一起送回去，後端比對存檔當下是不是還是同一個版本（見 TrainingPlanService.assertNotStale）
        model.addAttribute("planUpdatedAt", p.getUpdatedAt() == null ? "" : p.getUpdatedAt().toString());
        Map<String, Object> prs = new HashMap<>();
        for (LiftPr pr : liftPrService.findByUser(user)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("w", pr.getWeightKg());
            entry.put("r", pr.getReps());
            entry.put("sets", pr.getSets());
            entry.put("rest", pr.getRestSeconds());
            prs.put(pr.getExerciseName(), entry);
        }
        model.addAttribute("planPrs", prs);
        // 依週期階段分開存的手動覆寫（組數/次數/休息、配件重量）：在最大力量期存的 5x5 不能套用到
        // 肌耐力期/肌肥大期，所以前端不能只拿到一份「不分階段」的覆寫，要整包 phaseKey -> 內容都給前端，
        // 由前端自己依目前畫面上的階段（含減量週，後端不知道這個純前端狀態）去挑對應那份
        Map<String, Object> phasePrs = new HashMap<>();
        for (LiftPr pr : liftPrService.findByUser(user)) {
            Map<String, Object> phases = new HashMap<>();
            liftPrService.phaseOverridesOf(pr).forEach((phaseKey, ov) -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("w", ov.weightKg());
                entry.put("sets", ov.sets());
                entry.put("r", ov.reps());
                entry.put("rest", ov.restSeconds());
                phases.put(phaseKey, entry);
            });
            if (!phases.isEmpty()) phasePrs.put(pr.getExerciseName(), phases);
        }
        model.addAttribute("planPhasePrs", phasePrs);
        // 新手模式主項重量進階用：每個主項最近一次「真的完成」的實際重量，前端拿來 +2.5/+5kg 疊加，
        // 不是套用 LiftPr（那個是給老手模式 %1RM 算重量用，語意不同、不能混用）。
        // stalled=true 代表最近連續好幾次都沒真的完成，前端要改成建議降重量，不能再往上疊加
        Map<String, Object> mainProgress = new HashMap<>();
        workoutService.mainLiftProgress(user).forEach((name, progress) ->
                mainProgress.put(name, Map.of("w", progress.lastCompletedWeightKg(), "stalled", progress.stalled())));
        model.addAttribute("mainProgress", mainProgress);
        model.addAttribute("completedDays", workoutService.completedBodyPartsThisWeek(user));
        // 課表卡片組成（哪些主項/配件）現在真的查 Exercise 表選，不再是前端寫死的清單；
        // 帶週次讓配件池依「第幾週＋A/B」旋轉，同一天型態不會週週長一樣；
        // 帶 extraQueueCount 把「新增課表」多排出來、已持久化的張數重建回來，重新登入不會不見；
        // 使用者編輯過某天型態卡片的動作組成也要套用，不然重新整理又會被自動排的組成蓋掉
        List<WorkoutPlanService.DayComposition> planQueue =
                workoutPlanService.currentQueueComposition(user, p.getDaysPerWeek(), week, p.getExtraQueueCount());
        Map<String, TrainingPlanService.CardOverride> cardOverrides = trainingPlanService.getCardOverrides(user);
        model.addAttribute("planQueue", trainingPlanService.applyOverrides(cardOverrides, planQueue));
        // 使用者已經手動編輯過的天型態：這幾天的動作組成是使用者自己選的，不該再套用「約 60 分鐘」
        // 的自動排課時間預算去砍動作——那個預算只是給「系統自動排」的天型態當預設用的，使用者手動
        // 加的每一個都是特意要的，不能悄悄消失。沒編輯過的天型態則維持套用預算
        model.addAttribute("overriddenDayNames", cardOverrides.keySet());
        // 配件動作可以換成的清單，來源是 Exercise 表（排除四大主項；含使用者自己的個人自訂動作，
        // 不含別人的），給卡片編輯面板的搜尋式下拉選單用
        List<String> accessoryPool = exerciseService.findVisibleTo(user).stream()
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

    // 課表卡片編輯完（換動作/加/刪動作，以及每個動作的重量/組數/次數/休息）按「完成編輯」時存檔。
    // 動作組成（main/acc 名稱清單）用天型態名稱記住，下次同型態的卡片會直接套用；重量/組數/次數/休息
    // 則是用動作名稱存進 LiftPr，這樣同一個動作不管出現在哪個天型態、哪個位置都會套用同一組覆寫。
    // 表單送出的是「一個欄位一個平行 List」，跟 WorkoutController.zipExercises() 同一套手法，
    // 這裡先 zip 成本地的 CardExerciseSlot 再分別處理兩件事，不讓 controller 直接對著一堆平行 List 操作
    @PostMapping("/plan/card/save")
    public String saveCard(@RequestParam String dayName,
                           @RequestParam(required = false) String phase,
                           @RequestParam(required = false) String expectedUpdatedAt,
                           @RequestParam(required = false) List<String> main,
                           @RequestParam(required = false) List<String> acc,
                           @RequestParam(required = false) List<Double> mainWeights,
                           @RequestParam(required = false) List<Integer> mainSets,
                           @RequestParam(required = false) List<Integer> mainReps,
                           @RequestParam(required = false) List<Integer> mainRests,
                           @RequestParam(required = false) List<Double> accWeights,
                           @RequestParam(required = false) List<Integer> accSets,
                           @RequestParam(required = false) List<Integer> accReps,
                           @RequestParam(required = false) List<Integer> accRests,
                           @RequestParam(required = false) List<String> mainDirty,
                           @RequestParam(required = false) List<String> accDirty) {
        List<String> mainNames = main == null ? List.of() : main;
        List<String> accNames = acc == null ? List.of() : acc;
        if (mainNames.isEmpty() && accNames.isEmpty()) {
            throw new IllegalArgumentException("課表卡片至少要留一個動作");
        }
        User user = currentUserService.getCurrentUser();
        trainingPlanService.assertNotStale(user, expectedUpdatedAt);
        trainingPlanService.saveCardOverride(user, dayName, mainNames, accNames);

        List<CardExerciseSlot> slots = new ArrayList<>();
        slots.addAll(zipSlots(mainNames, mainWeights, mainSets, mainReps, mainRests, mainDirty));
        slots.addAll(zipSlots(accNames, accWeights, accSets, accReps, accRests, accDirty));
        // 只有使用者在彈窗裡真的改過那一列的數字（前端算出的 dirty 旗標）才存成手動覆寫——
        // 不然單純打開彈窗看一眼就按「完成編輯」，會把當下公式現算出來的數字（已經疊加過一次
        // 漸進幅度）誤存成新的手動覆寫，下次又再疊加一次，重量會無中生有一直往上跳
        for (CardExerciseSlot slot : slots) {
            if ("1".equals(slot.dirty()) && slot.weightKg() != null && slot.weightKg() > 0
                    && slot.sets() != null && slot.reps() != null && slot.restSeconds() != null) {
                liftPrService.saveManual(user, slot.name(), phase, slot.weightKg(), slot.sets(), slot.reps(), slot.restSeconds());
            }
        }
        return "redirect:/plan?saved";
    }

    private record CardExerciseSlot(String name, Double weightKg, Integer sets, Integer reps, Integer restSeconds, String dirty) {}

    private List<CardExerciseSlot> zipSlots(List<String> names, List<Double> weights, List<Integer> sets,
                                            List<Integer> reps, List<Integer> rests, List<String> dirty) {
        if (names == null) return List.of();
        List<CardExerciseSlot> result = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            result.add(new CardExerciseSlot(names.get(i), safeGet(weights, i), safeGet(sets, i),
                    safeGet(reps, i), safeGet(rests, i), safeGet(dirty, i)));
        }
        return result;
    }

    private <T> T safeGet(List<T> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    // 把某天型態的卡片重設回伺服器自動排的組成，取消先前存過的編輯
    @PostMapping("/plan/card/reset")
    public String resetCard(@RequestParam String dayName, @RequestParam(required = false) String expectedUpdatedAt) {
        User user = currentUserService.getCurrentUser();
        trainingPlanService.assertNotStale(user, expectedUpdatedAt);
        trainingPlanService.resetCardOverride(user, dayName);
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
