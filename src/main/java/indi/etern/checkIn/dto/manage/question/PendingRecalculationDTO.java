package indi.etern.checkIn.dto.manage.question;

import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.version.ScoreRecalculationLog;
import indi.etern.checkIn.service.dao.QuestionService;

import java.time.LocalDateTime;
import java.util.List;

public record PendingRecalculationDTO(
        String logId,
        String questionId,
        String questionVersionNumber,
        String triggerVersionId,
        String triggerVersionNumber,
        String questionContentPreview,
        String triggerType,
        LocalDateTime triggeredAt,
        Long triggeredByQq,
        int affectedExamCount,
        List<String> affectedExamIds
) {
    public static PendingRecalculationDTO from(ScoreRecalculationLog log, List<String> affectedExamIds) {
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
        
        return new PendingRecalculationDTO(
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
                affectedExamIds
        );
    }
}
