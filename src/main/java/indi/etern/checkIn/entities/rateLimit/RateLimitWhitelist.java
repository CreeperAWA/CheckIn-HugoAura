package indi.etern.checkIn.entities.rateLimit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "rate_limit_whitelist", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"dimension", "whitelist_value"})
})
@Getter
@Setter
public class RateLimitWhitelist {
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 20)
    private WhitelistDimension dimension;
    
    @Column(name = "whitelist_value", nullable = false, length = 255)
    private String whitelistValue;
    
    @Column(name = "description", length = 255)
    private String description;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum WhitelistDimension {
        IP, COOKIE, OAUTH
    }
}
