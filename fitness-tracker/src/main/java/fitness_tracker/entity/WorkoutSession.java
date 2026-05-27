package fitness_tracker.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workout_session")
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
               fetch = FetchType.EAGER)
    private List<WorkoutSet> sets = new ArrayList<>();

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

    // ── Setters ──────────────────────────────────────────
    public void setId(Long id)                          { this.id = id; }
    public void setWorkoutDate(LocalDate workoutDate)   { this.workoutDate = workoutDate; }
    public void setBodyPart(String bodyPart)            { this.bodyPart = bodyPart; }
    public void setNote(String note)                    { this.note = note; }
    public void setCreatedAt(LocalDateTime createdAt)   { this.createdAt = createdAt; }
    public void setSets(List<WorkoutSet> sets)          { this.sets = sets; }
}
