package indi.etern.checkIn.service.question;

import indi.etern.checkIn.dto.manage.question.CommonQuestionDTO;
import indi.etern.checkIn.dto.manage.question.MultipleChoicesQuestionDTO;
import indi.etern.checkIn.dto.manage.question.QuestionGroupDTO;
import indi.etern.checkIn.dto.manage.question.VersionInfoDTO;
import indi.etern.checkIn.entities.exam.ExamData;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.version.QuestionVersionChain;
import indi.etern.checkIn.repositories.QuestionVersionChainRepository;
import indi.etern.checkIn.service.dao.ExamDataService;
import indi.etern.checkIn.service.dao.QuestionService;
import indi.etern.checkIn.utils.QuestionCreateUtils;
import indi.etern.checkIn.utils.UUIDv7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class QuestionVersionService {
    
    private final QuestionService questionService;
    private final QuestionVersionChainRepository versionChainRepository;
    private final ExamDataService examDataService;
    private final QuestionChangeDetector changeDetector;
    
    public QuestionVersionService(QuestionService questionService,
                                  QuestionVersionChainRepository versionChainRepository,
                                  ExamDataService examDataService,
                                  QuestionChangeDetector changeDetector) {
        this.questionService = questionService;
        this.versionChainRepository = versionChainRepository;
        this.examDataService = examDataService;
        this.changeDetector = changeDetector;
    }
    
    public record VersionHandlingResult(
            Question question,
            boolean newVersionCreated,
            boolean recalculationNeeded
    ) {
        public static VersionHandlingResult noAction(Question q) {
            return new VersionHandlingResult(q, false, false);
        }
        public static VersionHandlingResult recalculationNeeded(Question q) {
            return new VersionHandlingResult(q, false, true);
        }
        public static VersionHandlingResult newVersionCreated(Question q, boolean recalc) {
            return new VersionHandlingResult(q, true, recalc);
        }
    }
    
    @Transactional
    public VersionHandlingResult handleVersionOnUpdate(
            Question previousQuestion,
            CommonQuestionDTO newQuestionDTO,
            Long currentUserQq) {
        
        if (previousQuestion == null) {
            return VersionHandlingResult.noAction(null);
        }
        
        QuestionChangeDetector.ChangeType changeType = changeDetector.detectChange(previousQuestion, newQuestionDTO);
        
        if (changeType == QuestionChangeDetector.ChangeType.NO_CHANGE) {
            return VersionHandlingResult.noAction(previousQuestion);
        }
        
        boolean hasHistoricalAnswers = hasHistoricalAnswers(previousQuestion.getId());
        
        if (!hasHistoricalAnswers) {
            return VersionHandlingResult.noAction(previousQuestion);
        }
        
        switch (changeType) {
            case ANSWER_KEY_CHANGE -> {
                return VersionHandlingResult.recalculationNeeded(previousQuestion);
            }
            case CONTENT_CHANGE, MIXED_CHANGE -> {
                Question newVersion = archiveAndCreateNewVersion(
                        previousQuestion, newQuestionDTO, changeType, currentUserQq);
                return VersionHandlingResult.newVersionCreated(newVersion, false);
            }
            default -> throw new IllegalStateException("Unexpected change type: " + changeType);
        }
    }
    
    private Question archiveAndCreateNewVersion(
            Question previousQuestion,
            CommonQuestionDTO newQuestionDTO,
            QuestionChangeDetector.ChangeType changeType,
            Long currentUserQq) {
        
        String versionGroupId = previousQuestion.getVersionGroupId() != null
                ? previousQuestion.getVersionGroupId() : previousQuestion.getId();
        int newVersionNumber = previousQuestion.getVersionNumber() + 1;
        
        // 1. Archive old version
        previousQuestion.setVersionStatus(Question.VersionStatus.ARCHIVED);
        previousQuestion.setEnabled(false);
        questionService.save(previousQuestion);
        
        // 2. Create new version entity with new ID (saved by caller after setting additional fields)
        Question newVersion = buildNewVersionFromDTO(newQuestionDTO, previousQuestion);
        newVersion.setVersionNumber(newVersionNumber);
        newVersion.setVersionGroupId(versionGroupId);
        newVersion.setPreviousVersionId(previousQuestion.getId());
        newVersion.setVersionStatus(Question.VersionStatus.ACTIVE);
        
        // 3. Record version chain
        QuestionVersionChain chain = QuestionVersionChain.builder()
                .id(UUIDv7.randomUUID().toString())
                .versionGroupId(versionGroupId)
                .fromVersionId(previousQuestion.getId())
                .toVersionId(newVersion.getId())
                .changeType(QuestionVersionChain.ChangeType.valueOf(changeType.name()))
                .createdAt(LocalDateTime.now())
                .createdByQq(currentUserQq)
                .build();
        versionChainRepository.save(chain);
        
        return newVersion;
    }
    
    private Question buildNewVersionFromDTO(CommonQuestionDTO dto, Question previousQuestion) {
        // Ensure partitionIds are set — fallback to previous version if missing
        if (dto.getPartitionIds() == null || dto.getPartitionIds().isEmpty()) {
            if (previousQuestion.getLinkWrapper() instanceof indi.etern.checkIn.entities.linkUtils.impl.ToPartitionsLink toPartitionsLink) {
                List<String> previousPartitionIds = toPartitionsLink.getTargets().stream()
                        .map(indi.etern.checkIn.entities.question.impl.Partition::getId)
                        .toList();
                if (!previousPartitionIds.isEmpty()) {
                    dto.setPartitionIds(previousPartitionIds);
                }
            }
        }
        if (dto instanceof MultipleChoicesQuestionDTO mcqDTO) {
            return QuestionCreateUtils.createMultipleChoicesQuestionForNewVersion(mcqDTO);
        } else if (dto instanceof QuestionGroupDTO qgDTO) {
            return QuestionCreateUtils.createQuestionGroupForNewVersion(qgDTO);
        }
        throw new IllegalArgumentException("Unknown DTO type: " + dto.getClass().getSimpleName());
    }
    
    private boolean hasHistoricalAnswers(String questionId) {
        List<ExamData> examDataList = examDataService.getExamDataContainsQuestionById(questionId);
        return examDataList.stream()
                .anyMatch(ed -> ed.getStatus() == ExamData.Status.SUBMITTED);
    }
    
    public List<VersionInfoDTO> getVersionHistory(String questionId) {
        Question question = questionService.findById(questionId).orElseThrow();
        String groupId = question.getVersionGroupId() != null
                ? question.getVersionGroupId() : question.getId();
        
        List<Question> allVersions = questionService.findByVersionGroupIdOrderByVersionNumberDesc(groupId);
        List<QuestionVersionChain> chains = versionChainRepository
                .findByVersionGroupIdOrderByCreatedAtDesc(groupId);
        
        Map<String, QuestionVersionChain> chainByToVersionId = new LinkedHashMap<>();
        for (QuestionVersionChain chain : chains) {
            chainByToVersionId.putIfAbsent(chain.getToVersionId(), chain);
        }
        
        List<VersionInfoDTO> result = new ArrayList<>();
        for (Question version : allVersions) {
            QuestionVersionChain chain = chainByToVersionId.get(version.getId());
            result.add(VersionInfoDTO.from(version, chain));
        }
        return result;
    }
    
    public Optional<Question> findActiveVersionByVersionGroupId(String versionGroupId) {
        List<Question> activeVersions = questionService.findByVersionGroupIdAndVersionStatus(
                versionGroupId, Question.VersionStatus.ACTIVE);
        return activeVersions.stream().findFirst();
    }
}
