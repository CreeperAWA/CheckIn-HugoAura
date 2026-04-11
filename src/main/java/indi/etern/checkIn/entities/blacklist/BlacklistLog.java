package indi.etern.checkIn.entities.blacklist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "blacklist_logs")
@Getter
@Setter
public class BlacklistLog {
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ActionType action;
    
    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "operated_by", length = 255)
    private String operatedBy;
    
    @Column(name = "operated_at", nullable = false, updatable = false)
    private LocalDateTime operatedAt;
    
    public enum ActionType {
        ADD, REMOVE
    }
}
