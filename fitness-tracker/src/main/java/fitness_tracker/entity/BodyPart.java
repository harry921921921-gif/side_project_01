package fitness_tracker.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "body_part")
public class BodyPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private int orderIndex = 0;

    @Column(nullable = false)
    private boolean preset = true;

    public BodyPart() {}

    public BodyPart(String name, int orderIndex) {
        this.name = name;
        this.orderIndex = orderIndex;
        this.preset = true;
    }

    public Long    getId()          { return id; }
    public String  getName()        { return name; }
    public int     getOrderIndex()  { return orderIndex; }
    public boolean isPreset()       { return preset; }

    public void setId(Long id)              { this.id = id; }
    public void setName(String name)        { this.name = name; }
    public void setOrderIndex(int idx)      { this.orderIndex = idx; }
    public void setPreset(boolean preset)   { this.preset = preset; }
}
