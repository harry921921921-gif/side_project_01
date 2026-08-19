package fitness_tracker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// 使用者的個人紀錄(PR)：每個主項最近一次的重量/次數與估算 1RM，登入後自動帶回，不用重填
@Entity
@Table(name = "lift_pr", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "exercise_name"}))
public class LiftPr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "exercise_name", nullable = false)
    private String exerciseName;

    private Double weightKg;
    private Integer reps;
    private Double oneRepMax;
    // 使用者在課表頁手動編輯動作時一起存的組數／休息秒數（可空——沒手動存過就沿用階段公式算出來的值）
    private Integer sets;
    private Integer restSeconds;
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void touch() { this.updatedAt = LocalDateTime.now(); }

    public Long getId()            { return id; }
    public User getUser()          { return user; }
    public String getExerciseName(){ return exerciseName; }
    public Double getWeightKg()    { return weightKg; }
    public Integer getReps()       { return reps; }
    public Double getOneRepMax()   { return oneRepMax; }
    public Integer getSets()       { return sets; }
    public Integer getRestSeconds(){ return restSeconds; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUser(User user)              { this.user = user; }
    public void setExerciseName(String n)       { this.exerciseName = n; }
    public void setWeightKg(Double w)           { this.weightKg = w; }
    public void setReps(Integer r)              { this.reps = r; }
    public void setOneRepMax(Double o)          { this.oneRepMax = o; }
    public void setSets(Integer s)              { this.sets = s; }
    public void setRestSeconds(Integer r)       { this.restSeconds = r; }
}
