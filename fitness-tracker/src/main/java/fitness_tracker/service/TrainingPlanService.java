package fitness_tracker.service;

import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.repository.TrainingPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 課表的後端真相來源：回答「今天/明天練什麼、目前第幾週/哪個階段」與「本週執行力」。
// 重量的「維持/進階」不在這裡決定 —— 那由每個動作自己的最後一次完成紀錄決定（之後的回饋閉環）。
@Service
public class TrainingPlanService {

    private static final Logger log = LoggerFactory.getLogger(TrainingPlanService.class);

    private final TrainingPlanRepository repo;
    private final WorkoutService workoutService;

    public TrainingPlanService(TrainingPlanRepository repo, WorkoutService workoutService) {
        this.repo = repo;
        this.workoutService = workoutService;
    }

    // ── 週期階段（與前端一致）：適應1–6 / 肌肥大7–14 / 最大力量15–20 ──
    public enum PhaseType {
        ADAPT("解剖適應", 1, 6),
        HYPER("肌肥大", 7, 14),
        STRENGTH("最大力量", 15, 20);
        public final String label;
        public final int start;
        public final int end;
        PhaseType(String label, int start, int end) { this.label = label; this.start = start; this.end = end; }
    }

    public record DaySplit(String name, List<String> mainLifts) {}
    public record DayPlan(boolean training, String dayName, List<String> mainLifts, String phaseLabel, int week) {}
    public record Adherence(int planned, int completed) {
        public int missed() { return Math.max(planned - completed, 0); }
    }

    // 分化：每天的主項（給脈絡用，配件略）
    private static final Map<Integer, List<DaySplit>> SPLITS = new HashMap<>();
    static {
        SPLITS.put(1, List.of(new DaySplit("全身", List.of("深蹲", "臥推"))));
        SPLITS.put(2, List.of(new DaySplit("上半身", List.of("臥推", "肩推")),
                              new DaySplit("下半身", List.of("深蹲"))));
        SPLITS.put(3, List.of(new DaySplit("推日", List.of("臥推", "肩推")),
                              new DaySplit("拉日", List.of("硬舉")),
                              new DaySplit("腿日", List.of("深蹲"))));
        SPLITS.put(4, List.of(new DaySplit("上肢 A", List.of("臥推", "肩推")),
                              new DaySplit("下肢 A", List.of("深蹲")),
                              new DaySplit("上肢 B", List.of("肩推", "臥推")),
                              new DaySplit("下肢 B", List.of("硬舉"))));
        SPLITS.put(5, List.of(new DaySplit("胸", List.of("臥推")),
                              new DaySplit("背", List.of("硬舉")),
                              new DaySplit("腿", List.of("深蹲")),
                              new DaySplit("肩", List.of("肩推")),
                              new DaySplit("手臂", List.of())));
        SPLITS.put(6, List.of(new DaySplit("推 A", List.of("臥推", "肩推")),
                              new DaySplit("拉 A", List.of("硬舉")),
                              new DaySplit("腿 A", List.of("深蹲")),
                              new DaySplit("推 B", List.of("肩推", "臥推")),
                              new DaySplit("拉 B", List.of("硬舉")),
                              new DaySplit("腿 B", List.of("深蹲"))));
    }

    @Transactional
    public TrainingPlan getOrCreateForUser(User user) {
        return repo.findByUser(user).orElseGet(() -> {
            TrainingPlan p = new TrainingPlan();
            p.setUser(user);
            p.setMode(PlanMode.NOVICE);
            p.setDaysPerWeek(3);
            p.setTrainingWeekdays("MONDAY,WEDNESDAY,FRIDAY");
            p.setPhaseStartDate(LocalDate.now());
            p.setStatus("ACTIVE");
            log.info("Creating default training plan for userId={}", user.getId());
            return repo.save(p);
        });
    }

    @Transactional
    public TrainingPlan saveOrUpdate(User user, PlanMode mode, int daysPerWeek,
                                     String weekdaysCsv, LocalDate phaseStartDate) {
        TrainingPlan p = repo.findByUser(user).orElseGet(TrainingPlan::new);
        p.setUser(user);
        p.setMode(mode == null ? PlanMode.NOVICE : mode);
        p.setDaysPerWeek(Math.min(Math.max(daysPerWeek, 1), 7));
        if (weekdaysCsv != null && !weekdaysCsv.isBlank()) p.setTrainingWeekdays(weekdaysCsv);
        if (phaseStartDate != null) p.setPhaseStartDate(phaseStartDate);
        if (p.getStatus() == null) p.setStatus("ACTIVE");
        log.info("Saving training plan for userId={}: mode={}, daysPerWeek={}", user.getId(), p.getMode(), p.getDaysPerWeek());
        return repo.save(p);
    }

    // 週次 = 今天與起算日相差幾週 + 1（隨時間自動前進）
    public int currentWeek(TrainingPlan p, LocalDate today) {
        long w = ChronoUnit.WEEKS.between(p.getPhaseStartDate(), today) + 1;
        return (int) Math.max(w, 1);
    }

    public PhaseType phaseForWeek(int week) {
        for (PhaseType pt : PhaseType.values()) {
            if (week <= pt.end) return pt;
        }
        return PhaseType.STRENGTH; // 超過 20 週先當最大力量期
    }

    private int splitDays(TrainingPlan p) {
        return Math.min(Math.max(p.getDaysPerWeek(), 1), 6);
    }

    // 某一天練什麼：把星期對應到分化第幾天，對不到就是休息日
    public DayPlan dayPlanFor(TrainingPlan p, LocalDate date) {
        int week = currentWeek(p, date);
        String phase = phaseForWeek(week).label;
        Set<DayOfWeek> days = p.weekdaySet();
        DayOfWeek dow = date.getDayOfWeek();
        if (!days.contains(dow)) {
            return new DayPlan(false, "休息日", List.of(), phase, week);
        }
        List<DayOfWeek> ordered = new ArrayList<>(days); // TreeSet → 已 Mon→Sun 排序
        int idx = ordered.indexOf(dow);
        List<DaySplit> split = SPLITS.getOrDefault(splitDays(p), SPLITS.get(3));
        if (idx < 0 || idx >= split.size()) {
            return new DayPlan(false, "休息日", List.of(), phase, week);
        }
        DaySplit ds = split.get(idx);
        return new DayPlan(true, ds.name(), ds.mainLifts(), phase, week);
    }

    @Transactional
    public DayPlan today(User user) {
        return dayPlanFor(getOrCreateForUser(user), LocalDate.now());
    }

    @Transactional
    public DayPlan tomorrow(User user) {
        return dayPlanFor(getOrCreateForUser(user), LocalDate.now().plusDays(1));
    }

    // 本週執行力：計畫幾練 vs 實際完成幾次（看週，不看特定某天）
    @Transactional
    public Adherence weeklyAdherence(User user) {
        TrainingPlan p = getOrCreateForUser(user);
        int planned = p.getDaysPerWeek();
        int completed = (int) workoutService.countThisWeek(user);
        return new Adherence(planned, completed);
    }
}
