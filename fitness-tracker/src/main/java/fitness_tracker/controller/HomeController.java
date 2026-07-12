package fitness_tracker.controller;

import fitness_tracker.service.BodyWeightService;
import fitness_tracker.service.SuggestionService;
import fitness_tracker.service.WorkoutService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller  // 告訴 Spring 這個 class 是 Controller（處理 HTTP 請求）
public class HomeController {

    private final BodyWeightService bodyWeightService;
    private final WorkoutService workoutService;
    private final SuggestionService suggestionService;

    public HomeController(BodyWeightService bodyWeightService, WorkoutService workoutService, SuggestionService suggestionService) {
        this.bodyWeightService = bodyWeightService;
        this.workoutService = workoutService;
        this.suggestionService = suggestionService;
    }

    /**
     * @GetMapping("/") → 當瀏覽器 GET 請求 http://localhost:8080/ 時執行
     * Model → 用來把資料傳到 HTML 模板
     * return "index" → 回傳 templates/index.html
     */
    @GetMapping("/")
    public String home(Model model) {
        // 查最新體重，放進 model 給 HTML 用
        bodyWeightService.findLatest()
                .ifPresent(w -> model.addAttribute("lastWeight", w));

        // 最近 7 天內的訓練（超過 7 天就不顯示）
        model.addAttribute("recentWorkouts", workoutService.findRecentWithinDays(7));

        // 本週訓練次數
        model.addAttribute("weeklyCount", workoutService.countThisWeek());

        // 訓練統計（本週次數、近 7 天複合動作訓練量依部位、平均 RPE、最近摘要、近 3 次完成率）
        model.addAttribute("dashboardStats", workoutService.computeDashboardStats());

        // 規則型訓練建議
        model.addAttribute("suggestions", suggestionService.generateSuggestions(workoutService.findRecentWithinDays(7)));

        return "index";
    }
}
