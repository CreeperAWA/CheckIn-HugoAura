package indi.etern.checkIn.entities.answerLimit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "answer_limit_whitelist")
@Getter
@Setter
public class AnswerLimitWhitelist {
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @Column(name = "qq", nullable = false, length = 255, unique = true)
    private String qq;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_qq")
    private Long createdByQQ;
}
