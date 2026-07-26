package fitness_tracker.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// AI 教練建議的快取：每人每天一筆，snapshotHash 相同就不重打 LLM
@Entity
@Table(name = "coach_advice")
public class CoachAdvice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate generatedDate;

    private String status;                       // ON_TRACK / SLIPPING / CRUSHING / CAUTION
    @Column(length = 500) private String executionLine;
    @Column(length = 500) private String todayLine;
    @Column(length = 500) private String tomorrowLine;
    private String snapshotHash;
    private LocalDateTime createdAt;

    @PrePersist @PreUpdate
    protected void touch() { this.createdAt = LocalDateTime.now(); }

    public Long getId()              { return id; }
    public User getUser()            { return user; }
    public LocalDate getGeneratedDate(){ return generatedDate; }
    public String getStatus()        { return status; }
    public String getExecutionLine() { return executionLine; }
    public String getTodayLine()     { return todayLine; }
    public String getTomorrowLine()  { return tomorrowLine; }
    public String getSnapshotHash()  { return snapshotHash; }

    public void setUser(User user)                 { this.user = user; }
    public void setGeneratedDate(LocalDate d)      { this.generatedDate = d; }
    public void setStatus(String status)           { this.status = status; }
    public void setExecutionLine(String v)         { this.executionLine = v; }
    public void setTodayLine(String v)             { this.todayLine = v; }
    public void setTomorrowLine(String v)          { this.tomorrowLine = v; }
    public void setSnapshotHash(String v)          { this.snapshotHash = v; }
}
