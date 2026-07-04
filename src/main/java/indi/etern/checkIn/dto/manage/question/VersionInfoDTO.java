package indi.etern.checkIn.dto.manage.question;

import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.version.QuestionVersionChain;

import java.time.LocalDateTime;

public record VersionInfoDTO(
        String questionId,
        int versionNumber,
        String versionStatus,
        String contentPreview,
        LocalDateTime lastModifiedTime,
        String changeType,
        String changeDescription,
        Long modifiedByQq,
        long examCount
) {
    public static VersionInfoDTO from(Question question, QuestionVersionChain chain, long examCount) {
        String contentPreview = null;
        if (question.getContent() != null) {
            contentPreview = question.getContent().length() > 100
                    ? question.getContent().substring(0, 100) + "..."
                    : question.getContent();
        }
        return new VersionInfoDTO(
                question.getId(),
                question.getVersionNumber(),
                question.getVersionStatus().name(),
                contentPreview,
                question.getLastModifiedTime(),
                chain != null ? chain.getChangeType().name() : null,
                chain != null ? chain.getChangeDescription() : null,
                chain != null ? chain.getCreatedByQq() : null,
                examCount
        );
    }
}
