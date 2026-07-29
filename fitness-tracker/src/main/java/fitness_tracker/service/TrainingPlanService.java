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
import java.util.List;
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

    public record DayPlan(boolean training, String dayName, List<String> mainLifts, String phaseLabel, int week) {}
    public record Adherence(int planned, int completed) {
        public int missed() { return Math.max(planned - completed, 0); }
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

    // 手動校正「目前第幾週」：只動 phaseStartDate，跟 saveOrUpdate（存模式/天數/PR）分開，
    // 避免使用者只是想存 PR，卻因為 slider 停在別的位置而把週次意外洗掉
    @Transactional
    public TrainingPlan setCurrentWeek(User user, int week) {
        TrainingPlan p = getOrCreateForUser(user);
        int clamped = Math.min(Math.max(week, 1), 104);
        p.setPhaseStartDate(LocalDate.now().minusWeeks(clamped - 1L));
        log.info("Manually setting current week for userId={} to week={}", user.getId(), clamped);
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
        List<SplitCatalog.DaySplitDef> split = SplitCatalog.forDays(p.getDaysPerWeek());
        if (idx < 0 || idx >= split.size()) {
            return new DayPlan(false, "休息日", List.of(), phase, week);
        }
        SplitCatalog.DaySplitDef ds = split.get(idx);
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
