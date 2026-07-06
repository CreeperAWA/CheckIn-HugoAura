package indi.etern.checkIn.service.question;

import indi.etern.checkIn.dto.manage.question.CommonQuestionDTO;
import indi.etern.checkIn.dto.manage.question.MultipleChoicesQuestionDTO;
import indi.etern.checkIn.dto.manage.question.QuestionGroupDTO;
import indi.etern.checkIn.dto.manage.question.VersionInfoDTO;
import indi.etern.checkIn.entities.exam.ExamData;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.version.QuestionVersionChain;
import indi.etern.checkIn.repositories.ExamDataRepository;
import indi.etern.checkIn.repositories.QuestionVersionChainRepository;
import indi.etern.checkIn.service.dao.ExamDataService;
import indi.etern.checkIn.service.dao.QuestionService;
import indi.etern.checkIn.utils.QuestionCreateUtils;
import indi.etern.checkIn.utils.UUIDv7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class QuestionVersionService {
    
    private final QuestionService questionService;
    private final QuestionVersionChainRepository versionChainRepository;
    private final ExamDataService examDataService;
    private final ExamDataRepository examDataRepository;
    private final QuestionChangeDetector changeDetector;
    
    public QuestionVersionService(QuestionService questionService,
                                  QuestionVersionChainRepository versionChainRepository,
                                  ExamDataService examDataService,
                                  ExamDataRepository examDataRepository,
                                  QuestionChangeDetector changeDetector) {
        this.questionService = questionService;
        this.versionChainRepository = versionChainRepository;
        this.examDataService = examDataService;
        this.examDataRepository = examDataRepository;
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
    public void initializeNewQuestionVersion(Question question, Long creatorQq) {
        String versionNumber = generateSha1Short(question.getId() + "|" + System.nanoTime());
        question.setVersionNumber(versionNumber);
        question.setVersionGroupId(question.getId());
        question.setVersionStatus(Question.VersionStatus.ACTIVE);
        
        QuestionVersionChain chain = QuestionVersionChain.builder()
                .id(UUIDv7.randomUUID().toString())
                .versionGroupId(question.getId())
                .fromVersionId(question.getId())
                .toVersionId(question.getId())
                .changeType(QuestionVersionChain.ChangeType.INITIAL)
                .createdAt(LocalDateTime.now())
                .createdByQq(creatorQq)
                .build();
        versionChainRepository.save(chain);
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
                Question newVersion = archiveAndCreateNewVersion(
                        previousQuestion, newQuestionDTO, changeType, currentUserQq);
                return VersionHandlingResult.newVersionCreated(newVersion, true);
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
        String newVersionNumber = generateSha1Short(previousQuestion.getId() + "|" + System.nanoTime());
        
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
    
    private String generateSha1Short(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 7);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
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
    
    @Transactional(readOnly = true)
    public List<VersionInfoDTO> getVersionHistory(String questionId) {
        Question question = questionService.findById(questionId).orElseThrow();
        String groupId = question.getVersionGroupId() != null
                ? question.getVersionGroupId() : question.getId();
        
        List<Question> allVersions = questionService.findByVersionGroupIdOrderByLastModifiedTimeDesc(groupId);
        List<QuestionVersionChain> chains = versionChainRepository
                .findByVersionGroupIdOrderByCreatedAtDesc(groupId);
        
        Map<String, QuestionVersionChain> chainByToVersionId = new LinkedHashMap<>();
        for (QuestionVersionChain chain : chains) {
            chainByToVersionId.putIfAbsent(chain.getToVersionId(), chain);
        }
        
        List<VersionInfoDTO> result = new ArrayList<>();
        for (Question version : allVersions) {
            QuestionVersionChain chain = chainByToVersionId.get(version.getId());
            long examCount = examDataRepository.countByQuestionIdsContains(version.getId());
            result.add(VersionInfoDTO.from(version, chain, examCount));
        }
        return result;
    }
    
    public Optional<Question> findActiveVersionByVersionGroupId(String versionGroupId) {
        List<Question> activeVersions = questionService.findByVersionGroupIdAndVersionStatus(
                versionGroupId, Question.VersionStatus.ACTIVE);
        return activeVersions.stream().findFirst();
    }
}
