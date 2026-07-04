package indi.etern.checkIn.entities.question.version;

import indi.etern.checkIn.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "score_change_detail")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ScoreChangeDetail implements BaseEntity<String> {
    
    @Id
    @Column(columnDefinition = "char(36)")
    String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recalculation_log_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    ScoreRecalculationLog recalculationLog;
    
    @Column(name = "exam_data_id", nullable = false, columnDefinition = "char(36)")
    String examDataId;
    
    @Column(name = "old_score", nullable = false)
    float oldScore;
    
    @Column(name = "new_score", nullable = false)
    float newScore;
    
    @Column(name = "old_level")
    String oldLevel;
    
    @Column(name = "new_level")
    String newLevel;
    
    @Column(name = "old_level_id", columnDefinition = "char(36)")
    String oldLevelId;
    
    @Column(name = "new_level_id", columnDefinition = "char(36)")
    String newLevelId;
    
    @Column(name = "score_changed", nullable = false)
    @Builder.Default
    boolean scoreChanged = false;
}
