package indi.etern.checkIn.dto.manage.question;

import indi.etern.checkIn.entities.question.version.ScoreChangeDetail;
import indi.etern.checkIn.entities.question.version.ScoreRecalculationLog;

import java.time.LocalDateTime;
import java.util.List;

public record RecalculationLogDTO(
        String id,
        String questionId,
        String questionContentPreview,
        String triggerType,
        LocalDateTime triggeredAt,
        Long triggeredByQq,
        int affectedExamCount,
        int scoreChangedExamCount,
        String status,
        LocalDateTime completedAt,
        String errorMessage,
        Long approvedByQq,
        LocalDateTime approvedAt,
        Long rejectedByQq,
        LocalDateTime rejectedAt,
        List<ScoreChangeDetailDTO> details
) {
    public static RecalculationLogDTO from(ScoreRecalculationLog log, List<ScoreChangeDetail> details) {
        return new RecalculationLogDTO(
                log.getId(),
                log.getQuestionId(),
                log.getQuestionContentPreview(),
                log.getTriggerType().name(),
                log.getTriggeredAt(),
                log.getTriggeredByQq(),
                log.getAffectedExamCount(),
                log.getScoreChangedExamCount(),
                log.getStatus().name(),
                log.getCompletedAt(),
                log.getErrorMessage(),
                log.getApprovedByQq(),
                log.getApprovedAt(),
                log.getRejectedByQq(),
                log.getRejectedAt(),
                details != null ? details.stream().map(ScoreChangeDetailDTO::from).toList() : null
        );
    }
}
