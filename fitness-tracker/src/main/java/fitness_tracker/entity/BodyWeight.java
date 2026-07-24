package fitness_tracker.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "body_weight")
public class BodyWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double weightKg;

    @Column(nullable = false)
    private LocalDate recordedDate;

    private String timeOfDay;    // MORNING / EVENING / OTHER

    private Double bodyFatPct;

    private Double skeletalMuscleKg;

    private String note;

    private LocalDateTime createdAt;

    // 擁有者；先開放 nullable，遷移完成前既有紀錄可能還沒有 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
//實務上會用 Lombok 來自動產生 Getter/Setter，這裡為了清楚展示所以手動寫出來
    // ── Getters ──────────────────────────────────────────
    public Long getId()                  { return id; }
    public Double getWeightKg()          { return weightKg; }
    public LocalDate getRecordedDate()   { return recordedDate; }
    public String getTimeOfDay()             { return timeOfDay; }
    public Double getBodyFatPct()            { return bodyFatPct; }
    public Double getSkeletalMuscleKg()      { return skeletalMuscleKg; }
    public String getNote()                  { return note; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
    public User getUser()                    { return user; }

    // ── Setters ──────────────────────────────────────────
    public void setId(Long id)                         { this.id = id; }
    public void setWeightKg(Double weightKg)           { this.weightKg = weightKg; }
    public void setRecordedDate(LocalDate recordedDate){ this.recordedDate = recordedDate; }
    public void setTimeOfDay(String timeOfDay)                     { this.timeOfDay = timeOfDay; }
    public void setBodyFatPct(Double bodyFatPct)                   { this.bodyFatPct = bodyFatPct; }
    public void setSkeletalMuscleKg(Double skeletalMuscleKg)       { this.skeletalMuscleKg = skeletalMuscleKg; }
    public void setNote(String note)                               { this.note = note; }
    public void setCreatedAt(LocalDateTime createdAt)              { this.createdAt = createdAt; }
    public void setUser(User user)                                 { this.user = user; }
}
