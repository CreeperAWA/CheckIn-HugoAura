package indi.etern.checkIn.entities.question.version;

import indi.etern.checkIn.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "score_recalculation_log")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ScoreRecalculationLog implements BaseEntity<String> {
    
    public enum TriggerType { ANSWER_KEY_CHANGE, MANUAL }
    public enum RecalculationStatus { AWAITING_APPROVAL, PENDING, IN_PROGRESS, COMPLETED, FAILED, REJECTED }
    
    @Id
    @Column(columnDefinition = "char(36)")
    String id;
    
    @Column(name = "question_id", nullable = false, columnDefinition = "char(36)")
    String questionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    TriggerType triggerType;
    
    @Column(name = "triggered_at", nullable = false)
    LocalDateTime triggeredAt;
    
    @Column(name = "triggered_by_qq")
    Long triggeredByQq;
    
    @Column(name = "affected_exam_count", nullable = false)
    @Builder.Default
    int affectedExamCount = 0;
    
    @Column(name = "score_changed_exam_count", nullable = false)
    @Builder.Default
    int scoreChangedExamCount = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    RecalculationStatus status;
    
    @Column(name = "completed_at")
    LocalDateTime completedAt;
    
    @Column(name = "error_message", columnDefinition = "text")
    String errorMessage;
    
    @Column(name = "approved_by_qq")
    Long approvedByQq;
    
    @Column(name = "approved_at")
    LocalDateTime approvedAt;
    
    @Column(name = "rejected_by_qq")
    Long rejectedByQq;
    
    @Column(name = "rejected_at")
    LocalDateTime rejectedAt;
    
    @Column(name = "question_content_preview")
    String questionContentPreview;
}
