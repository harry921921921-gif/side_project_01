package fitness_tracker.service;

import fitness_tracker.entity.TrainingPlan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 純函式版的週期階段計算，跟 TrainingPlanService.phaseForWeek 算的是同一件事，但故意獨立出來
// 不掛在 TrainingPlanService 上：TrainingPlanService 本來就依賴 WorkoutService（算本週執行力），
// 如果 WorkoutService 記錄真實訓練時反過來要注入 TrainingPlanService 算「這筆紀錄屬於哪個階段」，
// 會變成循環依賴，啟動時就炸掉。兩邊各自依賴這個沒有任何依賴的小工具類，就不會有這個問題。
public final class PhaseCalendar {
    private PhaseCalendar() {}

    public static final int TOTAL_WEEKS = 20;

    public enum PhaseType {
        ADAPT("adapt", 1, 6),
        HYPER("hyper", 7, 14),
        STRENGTH("strength", 15, 20);
        public final String key;
        public final int start;
        public final int end;
        PhaseType(String key, int start, int end) { this.key = key; this.start = start; this.end = end; }
    }

    public static int currentWeek(TrainingPlan p, LocalDate today) {
        long w = ChronoUnit.WEEKS.between(p.getPhaseStartDate(), today) + 1;
        return (int) Math.max(w, 1);
    }

    // 20 週跑完一輪（適應→肌肥大→最大力量）就從第 1 週重新開始一輪，不是練完第 20 週之後就一直卡在
    // 最大力量期不動——週期化訓練本來就該不斷循環，讓身體固定回到恢復期重新累積，一直停在最大力量期
    // 對長期使用者是錯的（長期都是高強度、沒有恢復期）。rawWeek 可以是任意大的數字（使用者練多久
    // 這個數字就一直長多大），這裡把它折回 1-20 的「這一輪第幾週」
    public static int cycleWeek(int rawWeek) {
        int w = Math.max(rawWeek, 1);
        return ((w - 1) % TOTAL_WEEKS) + 1;
    }

    public static PhaseType phaseForWeek(int week) {
        int w = cycleWeek(week);
        for (PhaseType pt : PhaseType.values()) {
            if (w <= pt.end) return pt;
        }
        return PhaseType.STRENGTH; // 理論上走不到，cycleWeek() 保證回傳值一定落在 1-20
    }
}
