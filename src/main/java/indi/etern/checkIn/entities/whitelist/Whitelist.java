package indi.etern.checkIn.entities.whitelist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "whitelist")
@Getter
@Setter
public class Whitelist {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "target_id", nullable = false, length = 255, unique = true)
    private String targetId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_qq")
    private Long createdByQQ;
}
