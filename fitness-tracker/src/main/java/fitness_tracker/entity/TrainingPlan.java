package fitness_tracker.entity;

import fitness_tracker.enums.PlanMode;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;

// 每位使用者一份 ACTIVE 訓練計畫。存的是「起算日 + 分化 + 練哪幾天」，
// 週次與階段由 phaseStartDate 動態算出，不用手動改。
@Entity
@Table(name = "training_plan")
public class TrainingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanMode mode = PlanMode.NOVICE;

    @Column(nullable = false)
    private int daysPerWeek = 3;

    // DayOfWeek 的 CSV，例如 "MONDAY,WEDNESDAY,FRIDAY"
    @Column(nullable = false, length = 120)
    private String trainingWeekdays = "MONDAY,WEDNESDAY,FRIDAY";

    @Column(nullable = false)
    private LocalDate phaseStartDate;

    // 「本週完整課表」用「新增課表」多排出來的張數（超出當週分化基本張數的部分）。
    // 換天數或手動校正週次時歸零，其餘情況（含重新登入）都要保留，不然使用者剛排好的課表一登出就不見。
    @Column(nullable = false)
    private int extraQueueCount = 0;

    // 選填：BULK / CUT / MAINTAIN，只當 AI 語氣標籤，不影響重量
    private String bodyGoal;

    // 使用者編輯過某天型態（例如「上肢 A」）課表卡片的動作組成（換動作/加/刪動作）時存這裡，
    // key 是天型態名稱、value 是 TrainingPlanService.CardOverride 序列化後的 JSON。
    // 用天型態名稱而不是佇列位置當 key，因為位置會隨完成進度、週次一直變，型態名稱才是使用者真正想固定下來的東西
    @Column(columnDefinition = "TEXT")
    private String cardOverridesJson;

    @Column(nullable = false)
    private String status = "ACTIVE";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.phaseStartDate == null) this.phaseStartDate = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 把 CSV 轉成排序好的星期集合（TreeSet 依 Mon→Sun 排序）
    @Transient
    public Set<DayOfWeek> weekdaySet() {
        Set<DayOfWeek> s = new TreeSet<>();
        if (trainingWeekdays != null && !trainingWeekdays.isBlank()) {
            for (String part : trainingWeekdays.split(",")) {
                try { s.add(DayOfWeek.valueOf(part.trim())); } catch (IllegalArgumentException ignored) {}
            }
        }
        return s;
    }

    public Long getId()                  { return id; }
    public User getUser()                { return user; }
    public PlanMode getMode()            { return mode; }
    public int getDaysPerWeek()          { return daysPerWeek; }
    public String getTrainingWeekdays()  { return trainingWeekdays; }
    public LocalDate getPhaseStartDate() { return phaseStartDate; }
    public int getExtraQueueCount()      { return extraQueueCount; }
    public String getBodyGoal()          { return bodyGoal; }
    public String getCardOverridesJson() { return cardOverridesJson; }
    public String getStatus()            { return status; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public LocalDateTime getUpdatedAt()  { return updatedAt; }

    public void setUser(User user)                       { this.user = user; }
    public void setMode(PlanMode mode)                   { this.mode = mode; }
    public void setDaysPerWeek(int daysPerWeek)          { this.daysPerWeek = daysPerWeek; }
    public void setTrainingWeekdays(String w)            { this.trainingWeekdays = w; }
    public void setPhaseStartDate(LocalDate d)           { this.phaseStartDate = d; }
    public void setExtraQueueCount(int n)                { this.extraQueueCount = Math.max(n, 0); }
    public void setBodyGoal(String bodyGoal)             { this.bodyGoal = bodyGoal; }
    public void setCardOverridesJson(String json)        { this.cardOverridesJson = json; }
    public void setStatus(String status)                 { this.status = status; }
}
