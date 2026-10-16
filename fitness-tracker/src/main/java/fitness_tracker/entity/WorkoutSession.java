package fitness_tracker.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// user_id+workout_date 幾乎每支查詢都會用到（依日期範圍查某使用者的訓練紀錄），
// 沒有索引的話資料量大了之後這裡會是第一個變慢的地方
@Entity
@Table(name = "workout_session", indexes = {
        @Index(name = "idx_workout_session_user_date", columnList = "user_id, workout_date")
})
public class WorkoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate workoutDate;

    private String bodyPart;

    private String note;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "session",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<WorkoutSet> sets = new ArrayList<>();

    // 擁有者；先開放 nullable，遷移完成前既有紀錄可能還沒有 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Getters ──────────────────────────────────────────
    public Long getId()                  { return id; }
    public LocalDate getWorkoutDate()    { return workoutDate; }
    public String getBodyPart()          { return bodyPart; }
    public String getNote()              { return note; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public List<WorkoutSet> getSets()    { return sets; }
    public User getUser()                { return user; }

    // ── Setters ──────────────────────────────────────────
    public void setId(Long id)                          { this.id = id; }
    public void setWorkoutDate(LocalDate workoutDate)   { this.workoutDate = workoutDate; }
    public void setBodyPart(String bodyPart)            { this.bodyPart = bodyPart; }
    public void setNote(String note)                    { this.note = note; }
    public void setCreatedAt(LocalDateTime createdAt)   { this.createdAt = createdAt; }
    public void setSets(List<WorkoutSet> sets)          { this.sets = sets; }
    public void setUser(User user)                      { this.user = user; }
}
