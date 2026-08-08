package fitness_tracker.entity;

import fitness_tracker.enums.Role;
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

    // 故意留 nullable：沒有遷移工具（ddl-auto=update），舊帳號這欄位加上去時是 NULL，
    // 用 getRole() 把 NULL 當成 USER 處理，不需要一次性把既有資料表都補值
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Long getId()               { return id; }
    public String getEmail()          { return email; }
    public String getPasswordHash()   { return passwordHash; }
    public String getDisplayName()    { return displayName; }
    public boolean isEnabled()        { return enabled; }
    public Role getRole()             { return role == null ? Role.USER : role; }
    public LocalDateTime getCreatedAt(){ return createdAt; }

    public void setEmail(String email)                { this.email = email; }
    public void setPasswordHash(String passwordHash)  { this.passwordHash = passwordHash; }
    public void setDisplayName(String displayName)    { this.displayName = displayName; }
    public void setEnabled(boolean enabled)           { this.enabled = enabled; }
    public void setRole(Role role)                    { this.role = role; }
}
