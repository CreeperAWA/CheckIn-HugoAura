package indi.etern.checkIn.dto.manage.question;

import indi.etern.checkIn.entities.question.version.ScoreChangeDetail;

public record ScoreChangeDetailDTO(
        String examDataId,
        float oldScore,
        float newScore,
        String oldLevel,
        String newLevel,
        String oldLevelId,
        String newLevelId,
        boolean scoreChanged
) {
    public static ScoreChangeDetailDTO from(ScoreChangeDetail detail) {
        return new ScoreChangeDetailDTO(
                detail.getExamDataId(),
                detail.getOldScore(),
                detail.getNewScore(),
                detail.getOldLevel(),
                detail.getNewLevel(),
                detail.getOldLevelId(),
                detail.getNewLevelId(),
                detail.isScoreChanged()
        );
    }
}
