package indi.etern.checkIn.dto.manage.question;

import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.version.ScoreChangeDetail;
import indi.etern.checkIn.entities.question.version.ScoreRecalculationLog;
import indi.etern.checkIn.service.dao.QuestionService;

import java.time.LocalDateTime;
import java.util.List;

public record RecalculationLogDTO(
        String id,
        String questionId,
        String questionVersionNumber,
        String triggerVersionId,
        String triggerVersionNumber,
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
        // Get version numbers from question entities
        String questionVersionNumber = QuestionService.singletonInstance.findById(log.getQuestionId())
                .map(Question::getVersionNumber)
                .orElse(null);
        String triggerVersionNumber = null;
        if (log.getTriggerVersionId() != null) {
            triggerVersionNumber = QuestionService.singletonInstance.findById(log.getTriggerVersionId())
                    .map(Question::getVersionNumber)
                    .orElse(null);
        }
        
        return new RecalculationLogDTO(
                log.getId(),
                log.getQuestionId(),
                questionVersionNumber,
                log.getTriggerVersionId(),
                triggerVersionNumber,
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
