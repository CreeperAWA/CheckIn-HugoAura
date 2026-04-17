package indi.etern.checkIn.entities.qqverify;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "qq_verify_record")
@Getter
@Setter
public class QQVerifyRecord {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "qq", nullable = false, length = 255)
    private String qq;

    @Column(name = "verify_content", nullable = false, columnDefinition = "TEXT")
    private String verifyContent;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // success, failed, no_need, timeout, cannot_verify

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
}
