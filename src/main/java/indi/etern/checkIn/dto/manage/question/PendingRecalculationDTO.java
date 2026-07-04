package indi.etern.checkIn.dto.manage.question;

import indi.etern.checkIn.entities.question.version.ScoreRecalculationLog;

import java.time.LocalDateTime;
import java.util.List;

public record PendingRecalculationDTO(
        String logId,
        String questionId,
        String questionContentPreview,
        String triggerType,
        LocalDateTime triggeredAt,
        Long triggeredByQq,
        int affectedExamCount,
        List<String> affectedExamIds
) {
    public static PendingRecalculationDTO from(ScoreRecalculationLog log, List<String> affectedExamIds) {
        return new PendingRecalculationDTO(
                log.getId(),
                log.getQuestionId(),
                log.getQuestionContentPreview(),
                log.getTriggerType().name(),
                log.getTriggeredAt(),
                log.getTriggeredByQq(),
                log.getAffectedExamCount(),
                affectedExamIds
        );
    }
}
