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

    // ── Getters ──────────────────────────────────────────
    public Long getId()                  { return id; }
    public WorkoutSession getSession()   { return session; }
    public String getExerciseName()      { return exerciseName; }
    public Double getWeightKg()          { return weightKg; }
    public Integer getSets()             { return sets; }
    public Integer getReps()             { return reps; }

    // ── Setters ──────────────────────────────────────────
    public void setId(Long id)                          { this.id = id; }
    public void setSession(WorkoutSession session)      { this.session = session; }
    public void setExerciseName(String exerciseName)    { this.exerciseName = exerciseName; }
    public void setWeightKg(Double weightKg)            { this.weightKg = weightKg; }
    public void setSets(Integer sets)                   { this.sets = sets; }
    public void setReps(Integer reps)                   { this.reps = reps; }
}
