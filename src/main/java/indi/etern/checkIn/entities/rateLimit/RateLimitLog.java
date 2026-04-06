package indi.etern.checkIn.entities.rateLimit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "rate_limit_logs", indexes = {
        @Index(name = "idx_ip", columnList = "ip_address"),
        @Index(name = "idx_qq", columnList = "qq_number"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_triggered_dimension", columnList = "triggered_dimension")
})
@Getter
@Setter
public class RateLimitLog {
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "cookie_value", length = 255)
    private String cookieValue;
    
    @Column(name = "qq_number")
    private Long qqNumber;
    
    @Column(name = "oauth_info", length = 255)
    private String oauthInfo;
    
    @Column(name = "request_path", nullable = false, length = 500)
    private String requestPath;
    
    @Column(name = "request_method", nullable = false, length = 10)
    private String requestMethod;
    
    @Column(name = "triggered_rule_id", length = 36)
    private String triggeredRuleId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_dimension", length = 20)
    private RateLimitRule.RateLimitDimension triggeredDimension;
    
    @Column(name = "response_action", nullable = false, length = 50)
    private String responseAction;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
