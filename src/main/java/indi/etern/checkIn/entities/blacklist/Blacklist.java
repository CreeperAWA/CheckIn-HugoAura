package indi.etern.checkIn.entities.blacklist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "blacklist")
@Getter
@Setter
public class Blacklist {
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @Column(name = "target_id", nullable = false, length = 255, unique = true)
    private String targetId;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", length = 255)
    private String createdBy;
}
