package indi.etern.checkIn.service.question;

public record ScoreRecalculationEvent(
        String questionId,
        String logId,
        Long triggeredByQq
) {}
