package fitness_tracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.enums.PlanMode;
import fitness_tracker.repository.TrainingPlanRepository;
import fitness_tracker.service.WorkoutPlanService.DayComposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 課表的後端真相來源：回答「今天/明天練什麼、目前第幾週/哪個階段」與「本週執行力」。
// 重量的「維持/進階」不在這裡決定 —— 那由每個動作自己的最後一次完成紀錄決定（之後的回饋閉環）。
@Service
public class TrainingPlanService {

    private static final Logger log = LoggerFactory.getLogger(TrainingPlanService.class);

    private final TrainingPlanRepository repo;
    private final WorkoutService workoutService;
    private final ObjectMapper objectMapper;

    public TrainingPlanService(TrainingPlanRepository repo, WorkoutService workoutService, ObjectMapper objectMapper) {
        this.repo = repo;
        this.workoutService = workoutService;
        this.objectMapper = objectMapper;
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
    public record CardOverride(List<String> main, List<String> acc) {}

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
        Optional<TrainingPlan> existing = repo.findByUser(user);
        TrainingPlan p = existing.orElseGet(TrainingPlan::new);
        int clampedDays = Math.min(Math.max(daysPerWeek, 1), 7);
        // 天數真的改變才歸零「新增課表」多排出來的張數——分化整個不一樣了，舊的延伸卡片沒意義了
        if (existing.isPresent() && p.getDaysPerWeek() != clampedDays) {
            p.setExtraQueueCount(0);
        }
        p.setUser(user);
        p.setMode(mode == null ? PlanMode.NOVICE : mode);
        p.setDaysPerWeek(clampedDays);
        if (weekdaysCsv != null && !weekdaysCsv.isBlank()) p.setTrainingWeekdays(weekdaysCsv);
        if (phaseStartDate != null) p.setPhaseStartDate(phaseStartDate);
        if (p.getStatus() == null) p.setStatus("ACTIVE");
        log.info("Saving training plan for userId={}: mode={}, daysPerWeek={}", user.getId(), p.getMode(), p.getDaysPerWeek());
        return repo.save(p);
    }

    // 手動校正「目前第幾週」：只動 phaseStartDate，跟 saveOrUpdate（存模式/天數/PR）分開，
    // 避免使用者只是想存 PR，卻因為 slider 停在別的位置而把週次意外洗掉。
    // 這算使用者主動「更改訓練週期」，「新增課表」多排出來的張數一併歸零。
    @Transactional
    public TrainingPlan setCurrentWeek(User user, int week) {
        TrainingPlan p = getOrCreateForUser(user);
        int clamped = Math.min(Math.max(week, 1), 104);
        p.setPhaseStartDate(LocalDate.now().minusWeeks(clamped - 1L));
        p.setExtraQueueCount(0);
        log.info("Manually setting current week for userId={} to week={}", user.getId(), clamped);
        return repo.save(p);
    }

    // 使用者按一次「新增課表」，「本週完整課表」多排出來的張數就＋1，換登入裝置/重新整理都要保留
    @Transactional
    public int incrementExtraQueueCount(User user) {
        TrainingPlan p = getOrCreateForUser(user);
        p.setExtraQueueCount(p.getExtraQueueCount() + 1);
        repo.save(p);
        return p.getExtraQueueCount();
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

    // ── 課表卡片的動作組成覆寫：使用者編輯過某天型態的卡片（換動作/加/刪動作）就記住，
    //    不然重新整理又會被伺服器自動排的組成蓋掉。key 用天型態名稱，不是佇列位置 ──
    @Transactional(readOnly = true)
    public Map<String, CardOverride> getCardOverrides(User user) {
        return parseOverrides(getOrCreateForUser(user).getCardOverridesJson());
    }

    @Transactional
    public void saveCardOverride(User user, String dayName, List<String> main, List<String> acc) {
        TrainingPlan p = getOrCreateForUser(user);
        Map<String, CardOverride> overrides = new LinkedHashMap<>(parseOverrides(p.getCardOverridesJson()));
        overrides.put(dayName, new CardOverride(main, acc));
        p.setCardOverridesJson(writeOverrides(overrides));
        repo.save(p);
    }

    @Transactional
    public void resetCardOverride(User user, String dayName) {
        TrainingPlan p = getOrCreateForUser(user);
        Map<String, CardOverride> overrides = new LinkedHashMap<>(parseOverrides(p.getCardOverridesJson()));
        overrides.remove(dayName);
        p.setCardOverridesJson(writeOverrides(overrides));
        repo.save(p);
    }

    // 把使用者存過的覆寫套進伺服器自動算出來的課表組成；沒被使用者動過的天型態照舊回傳自動算的結果
    public List<DayComposition> applyOverrides(Map<String, CardOverride> overrides, List<DayComposition> compositions) {
        if (overrides.isEmpty()) return compositions;
        return compositions.stream()
                .map(dc -> {
                    CardOverride ov = overrides.get(dc.dayName());
                    return ov == null ? dc : new DayComposition(dc.dayName(), ov.main(), ov.acc());
                })
                .toList();
    }

    private Map<String, CardOverride> parseOverrides(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, CardOverride>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse card overrides, ignoring: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String writeOverrides(Map<String, CardOverride> overrides) {
        try {
            return objectMapper.writeValueAsString(overrides);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize card overrides", ex);
        }
    }
}
