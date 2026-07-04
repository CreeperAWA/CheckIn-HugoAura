package indi.etern.checkIn.entities.question.version;

import indi.etern.checkIn.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_version_chain")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuestionVersionChain implements BaseEntity<String> {
    
    public enum ChangeType {
        CONTENT_CHANGE, ANSWER_KEY_CHANGE, MIXED_CHANGE
    }
    
    @Id
    @Column(columnDefinition = "char(36)")
    String id;
    
    @Column(name = "version_group_id", nullable = false, columnDefinition = "char(36)")
    String versionGroupId;
    
    @Column(name = "from_version_id", nullable = false, columnDefinition = "char(36)")
    String fromVersionId;
    
    @Column(name = "to_version_id", nullable = false, columnDefinition = "char(36)")
    String toVersionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    ChangeType changeType;
    
    @Column(name = "change_description", columnDefinition = "text")
    String changeDescription;
    
    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;
    
    @Column(name = "created_by_qq")
    Long createdByQq;
}
