package fitness_tracker.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "exercise")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;        // 臥推、深蹲、硬舉 ...

    @Column(nullable = false)
    private String bodyPart;    // 胸、背、腿、肩、手臂、核心

    @Column(nullable = false)
    private String category;    // COMPOUND / ISOLATION

    @Column(nullable = false)
    private boolean preset = true;  // true=系統預設  false=用戶自訂

    private Integer orderIndex = 0;

    public Exercise() {}

    public Exercise(String name, String bodyPart, String category) {
        this.name     = name;
        this.bodyPart = bodyPart;
        this.category = category;
        this.preset   = true;
    }

    // ── Getters ──────────────────────────────────────────
    public Long    getId()            { return id; }
    public String  getName()          { return name; }
    public String  getBodyPart()      { return bodyPart; }
    public String  getCategory()      { return category; }
    public boolean isPreset()         { return preset; }
    public Integer getOrderIndex()    { return orderIndex; }

    // ── Setters ──────────────────────────────────────────
    public void setId(Long id)                      { this.id = id; }
    public void setName(String name)                { this.name = name; }
    public void setBodyPart(String bp)              { this.bodyPart = bp; }
    public void setCategory(String cat)             { this.category = cat; }
    public void setPreset(boolean preset)           { this.preset = preset; }
    public void setOrderIndex(Integer orderIndex)   { this.orderIndex = orderIndex; }
}
