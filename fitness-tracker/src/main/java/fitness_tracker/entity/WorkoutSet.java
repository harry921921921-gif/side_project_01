package fitness_tracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "workout_set")
public class WorkoutSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSession session;

    @Column(nullable = false)
    private String exerciseName;

    private Double weightKg;

    private Integer sets;

    private Integer reps;

    private Integer restSeconds;

    // 主觀強度（RPE，例如 6.0 ~ 10.0）
    private Double rpe;

    // 完成狀態：COMPLETE / FAILED / DROPPED / PAIN
    private String completionStatus;

    // 實際完成的次數與重量（若與計劃不同）
    private Integer actualReps;

    private Double actualWeight;

    private String notes;

    // ── Getters ──────────────────────────────────────────
    public Long getId()                  { return id; }
    public WorkoutSession getSession()   { return session; }
    public String getExerciseName()      { return exerciseName; }
    public Double getWeightKg()          { return weightKg; }
    public Integer getSets()             { return sets; }
    public Integer getReps()             { return reps; }
    public Integer getRestSeconds()      { return restSeconds; }
    public Double getRpe()               { return rpe; }
    public String getCompletionStatus()  { return completionStatus; }
    public Integer getActualReps()       { return actualReps; }
    public Double getActualWeight()      { return actualWeight; }
    public String getNotes()             { return notes; }

    // ── Setters ──────────────────────────────────────────
    public void setId(Long id)                          { this.id = id; }
    public void setSession(WorkoutSession session)      { this.session = session; }
    public void setExerciseName(String exerciseName)    { this.exerciseName = exerciseName; }
    public void setWeightKg(Double weightKg)            { this.weightKg = weightKg; }
    public void setSets(Integer sets)                   { this.sets = sets; }
    public void setReps(Integer reps)                   { this.reps = reps; }
    public void setRestSeconds(Integer restSeconds)     { this.restSeconds = restSeconds; }
    public void setRpe(Double rpe)                              { this.rpe = rpe; }
    public void setCompletionStatus(String completionStatus)    { this.completionStatus = completionStatus; }
    public void setActualReps(Integer actualReps)               { this.actualReps = actualReps; }
    public void setActualWeight(Double actualWeight)            { this.actualWeight = actualWeight; }
    public void setNotes(String notes)                          { this.notes = notes; }
}
