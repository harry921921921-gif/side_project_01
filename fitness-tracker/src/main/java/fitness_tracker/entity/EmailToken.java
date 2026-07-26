package fitness_tracker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// 帳號驗證 / 密碼重設共用的一次性 token
@Entity
@Table(name = "email_token")
public class EmailToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String type;              // VERIFY / RESET

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Long getId()             { return id; }
    public String getToken()        { return token; }
    public User getUser()           { return user; }
    public String getType()         { return type; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isUsed()         { return used; }

    public void setToken(String token)              { this.token = token; }
    public void setUser(User user)                  { this.user = user; }
    public void setType(String type)                { this.type = type; }
    public void setExpiresAt(LocalDateTime t)       { this.expiresAt = t; }
    public void setUsed(boolean used)               { this.used = used; }
}
