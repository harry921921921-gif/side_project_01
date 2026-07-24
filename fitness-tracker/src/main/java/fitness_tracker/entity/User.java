package fitness_tracker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // utf8mb4_bin：大小寫敏感，Test@x.com 與 test@x.com 視為不同帳號
    @Column(nullable = false, unique = true, columnDefinition = "VARCHAR(255) COLLATE utf8mb4_bin")
    private String email;

    @Column(nullable = false)
    private String passwordHash;   // BCrypt 雜湊，永不存明碼

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Long getId()               { return id; }
    public String getEmail()          { return email; }
    public String getPasswordHash()   { return passwordHash; }
    public String getDisplayName()    { return displayName; }
    public boolean isEnabled()        { return enabled; }
    public LocalDateTime getCreatedAt(){ return createdAt; }

    public void setEmail(String email)                { this.email = email; }
    public void setPasswordHash(String passwordHash)  { this.passwordHash = passwordHash; }
    public void setDisplayName(String displayName)    { this.displayName = displayName; }
    public void setEnabled(boolean enabled)           { this.enabled = enabled; }
}
