package indi.etern.checkIn.entities.rateLimit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "rate_limit_rules")
@Getter
@Setter
public class RateLimitRule {
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 20)
    private RateLimitDimension dimension;
    
    @Column(name = "enabled", nullable = false)
    private boolean enabled;
    
    @Column(name = "time_window_seconds", nullable = false)
    private int timeWindowSeconds;
    
    @Column(name = "max_requests", nullable = false)
    private int maxRequests;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "response_strategy", nullable = false, length = 20)
    private ResponseStrategy responseStrategy;
    
    @Column(name = "custom_message", columnDefinition = "TEXT")
    private String customMessage;
    
    @Column(name = "base_delay_ms", nullable = false)
    private int baseDelayMs;
    
    @Column(name = "priority", nullable = false)
    private int priority;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum RateLimitDimension {
        IP, COOKIE, QQ, OAUTH
    }
    
    public enum ResponseStrategy {
        RETURN_429, CUSTOM_MESSAGE, PROGRESSIVE_DELAY
    }
}
