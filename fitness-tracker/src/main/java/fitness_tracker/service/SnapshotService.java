package fitness_tracker.service;

import fitness_tracker.entity.User;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.service.TrainingPlanService.Adherence;
import fitness_tracker.service.TrainingPlanService.DayPlan;
import fitness_tracker.service.WorkoutService.DashboardStats;
import org.springframework.stereotype.Service;

import java.util.List;

// 把「事實」組成給 AI 教練的訓練快照：今天/明天/執行力/近期表現/警訊
@Service
public class SnapshotService {

    private final TrainingPlanService planService;
    private final WorkoutService workoutService;
    private final SuggestionService suggestionService;

    public SnapshotService(TrainingPlanService planService, WorkoutService workoutService, SuggestionService suggestionService) {
        this.planService = planService;
        this.workoutService = workoutService;
        this.suggestionService = suggestionService;
    }

    public record CoachSnapshot(int planned, int completed, DayPlan today, DayPlan tomorrow, Double avgRpe, boolean caution) {

        public String toPrompt() {
            String todayStr = today.training()
                    ? "練" + today.dayName() + (today.mainLifts().isEmpty() ? "" : "（主項" + String.join("、", today.mainLifts()) + "）")
                    : "休息日";
            String tmrStr = tomorrow.training() ? "練" + tomorrow.dayName() : "休息日";
            StringBuilder sb = new StringBuilder();
            sb.append("本週計畫 ").append(planned).append(" 練，已完成 ").append(completed).append(" 次。\n");
            if (avgRpe != null && avgRpe > 0) sb.append("近期平均 RPE：").append(String.format("%.1f", avgRpe)).append("。\n");
            sb.append("今天：").append(todayStr).append("，目前第 ").append(today.week()).append(" 週 · ").append(today.phaseLabel()).append("期。\n");
            sb.append("明天：").append(tmrStr).append("。\n");
            if (caution) sb.append("注意：最近出現疼痛或連續失敗。\n");
            return sb.toString();
        }

        public String hash() { return Integer.toHexString(toPrompt().hashCode()); }
    }

    public CoachSnapshot build(User user) {
        DayPlan today = planService.today(user);
        DayPlan tomorrow = planService.tomorrow(user);
        Adherence adh = planService.weeklyAdherence(user);
        DashboardStats stats = workoutService.computeDashboardStats(user);
        List<WorkoutSession> recent = workoutService.findRecentWithinDays(7, user);
        boolean caution = suggestionService.generateSuggestions(recent).stream()
                .anyMatch(s -> "DANGER".equals(s.level()));
        return new CoachSnapshot(adh.planned(), adh.completed(), today, tomorrow, stats.avgRpe(), caution);
    }
}
