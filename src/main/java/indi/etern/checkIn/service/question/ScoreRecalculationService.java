package indi.etern.checkIn.service.question;

import indi.etern.checkIn.api.webSocket.Message;
import indi.etern.checkIn.entities.exam.ExamData;
import indi.etern.checkIn.entities.linkUtils.impl.ToQuestionGroupLink;
import indi.etern.checkIn.entities.question.impl.Choice;
import indi.etern.checkIn.entities.question.impl.MultipleChoiceAnswer;
import indi.etern.checkIn.entities.question.impl.MultipleChoicesQuestion;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.impl.QuestionGroup;
import indi.etern.checkIn.entities.question.impl.QuestionGroupAnswer;
import indi.etern.checkIn.entities.question.interfaces.answer.Answer;
import indi.etern.checkIn.entities.question.interfaces.answer.SingleQuestionAnswer;
import indi.etern.checkIn.entities.question.version.ScoreChangeDetail;
import indi.etern.checkIn.entities.question.version.ScoreRecalculationLog;
import indi.etern.checkIn.entities.setting.SettingItem;
import indi.etern.checkIn.entities.setting.grading.GradingLevel;
import indi.etern.checkIn.repositories.ScoreChangeDetailRepository;
import indi.etern.checkIn.repositories.ScoreRecalculationLogRepository;
import indi.etern.checkIn.service.dao.ExamDataService;
import indi.etern.checkIn.service.dao.GradingLevelService;
import indi.etern.checkIn.service.dao.QuestionService;
import indi.etern.checkIn.service.dao.SettingService;
import indi.etern.checkIn.service.exam.ExamResult;
import indi.etern.checkIn.service.web.WebSocketService;
import indi.etern.checkIn.utils.UUIDv7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ScoreRecalculationService {
    
    private final ExamDataService examDataService;
    private final QuestionService questionService;
    private final GradingLevelService gradingLevelService;
    private final ScoreRecalculationLogRepository recalcLogRepository;
    private final ScoreChangeDetailRepository changeDetailRepository;
    private final SettingService settingService;
    private final ApplicationContext applicationContext;
    private final WebSocketService webSocketService;
    private final Logger logger = LoggerFactory.getLogger(ScoreRecalculationService.class);
    
    public ScoreRecalculationService(ExamDataService examDataService,
                                     QuestionService questionService,
                                     GradingLevelService gradingLevelService,
                                     ScoreRecalculationLogRepository recalcLogRepository,
                                     ScoreChangeDetailRepository changeDetailRepository,
                                     SettingService settingService,
                                     ApplicationContext applicationContext,
                                     WebSocketService webSocketService) {
        this.examDataService = examDataService;
        this.questionService = questionService;
        this.gradingLevelService = gradingLevelService;
        this.recalcLogRepository = recalcLogRepository;
        this.changeDetailRepository = changeDetailRepository;
        this.settingService = settingService;
        this.applicationContext = applicationContext;
        this.webSocketService = webSocketService;
    }
    
    public String triggerAsyncRecalculation(String questionId, String triggerVersionId, Long triggeredByQq) {
        List<ExamData> affectedExams = examDataService.getExamDataContainsQuestionById(questionId)
                .stream()
                .filter(ed -> ed.getStatus() == ExamData.Status.SUBMITTED)
                .toList();
        
        String contentPreview = questionService.findById(questionId)
                .map(q -> q.getContent() != null && q.getContent().length() > 100
                        ? q.getContent().substring(0, 100) + "..." : q.getContent())
                .orElse(null);
        
        ScoreRecalculationLog log = ScoreRecalculationLog.builder()
                .id(UUIDv7.randomUUID().toString())
                .questionId(questionId)
                .triggerVersionId(triggerVersionId)
                .triggerType(ScoreRecalculationLog.TriggerType.ANSWER_KEY_CHANGE)
                .triggeredAt(LocalDateTime.now())
                .triggeredByQq(triggeredByQq)
                .affectedExamCount(affectedExams.size())
                .questionContentPreview(contentPreview)
                .status(ScoreRecalculationLog.RecalculationStatus.AWAITING_APPROVAL)
                .build();
        recalcLogRepository.save(log);
        
        webSocketService.sendMessageToAllUsers(Message.of("recalculationAwaitingApproval", log.getId()));
        
        return log.getId();
    }
    
    public String triggerManualRecalculation(String questionId, Long triggeredByQq) {
        ScoreRecalculationLog log = ScoreRecalculationLog.builder()
                .id(UUIDv7.randomUUID().toString())
                .questionId(questionId)
                .triggerVersionId(null)  // Manual trigger has no version change
                .triggerType(ScoreRecalculationLog.TriggerType.MANUAL)
                .triggeredAt(LocalDateTime.now())
                .triggeredByQq(triggeredByQq)
                .status(ScoreRecalculationLog.RecalculationStatus.PENDING)
                .build();
        recalcLogRepository.save(log);
        
        applicationContext.publishEvent(new ScoreRecalculationEvent(questionId, log.getId(), triggeredByQq));
        return log.getId();
    }
    
    @Transactional
    public void approveRecalculation(String logId, Long approvedByQq) {
        ScoreRecalculationLog log = recalcLogRepository.findById(logId).orElseThrow(() -> new IllegalArgumentException("Recalculation log not found: " + logId));
        if (log.getStatus() != ScoreRecalculationLog.RecalculationStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException("Log is not in AWAITING_APPROVAL status");
        }
        log.setStatus(ScoreRecalculationLog.RecalculationStatus.PENDING);
        log.setApprovedByQq(approvedByQq);
        log.setApprovedAt(LocalDateTime.now());
        recalcLogRepository.save(log);
        
        applicationContext.publishEvent(new ScoreRecalculationEvent(log.getQuestionId(), log.getId(), approvedByQq));
    }
    
    @Transactional
    public void rejectRecalculation(String logId, Long rejectedByQq) {
        ScoreRecalculationLog log = recalcLogRepository.findById(logId).orElseThrow(() -> new IllegalArgumentException("Recalculation log not found: " + logId));
        if (log.getStatus() != ScoreRecalculationLog.RecalculationStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException("Log is not in AWAITING_APPROVAL status");
        }
        log.setStatus(ScoreRecalculationLog.RecalculationStatus.REJECTED);
        log.setRejectedByQq(rejectedByQq);
        log.setRejectedAt(LocalDateTime.now());
        recalcLogRepository.save(log);
    }
    
    public List<ScoreRecalculationLog> getPendingRecalculations() {
        return recalcLogRepository.findByStatusOrderByTriggeredAtDesc(
                ScoreRecalculationLog.RecalculationStatus.AWAITING_APPROVAL);
    }
    
    public List<String> getAffectedExamIds(String questionId) {
        return examDataService.getExamDataContainsQuestionById(questionId)
                .stream()
                .filter(ed -> ed.getStatus() == ExamData.Status.SUBMITTED)
                .map(ExamData::getId)
                .toList();
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeRecalculation(String questionId, String logId) {
        ScoreRecalculationLog recalcLog = recalcLogRepository.findById(logId).orElseThrow(() -> new IllegalArgumentException("Recalculation log not found: " + logId));
        recalcLog.setStatus(ScoreRecalculationLog.RecalculationStatus.IN_PROGRESS);
        recalcLogRepository.save(recalcLog);
        
        try {
            List<ExamData> affectedExams = examDataService.getExamDataContainsQuestionById(questionId)
                    .stream()
                    .filter(ed -> ed.getStatus() == ExamData.Status.SUBMITTED)
                    .toList();
            
            int affectedCount = affectedExams.size();
            int scoreChangedCount = 0;
            
            List<GradingLevel> gradingLevels = gradingLevelService.findAll();
            Float[] levelSplitArray = buildLevelSplitArray();
            
            // Resolve the new version question for answer checking
            Question newVersionQuestion = resolveNewVersionQuestion(questionId, recalcLog.getTriggerVersionId());
            
            for (ExamData examData : affectedExams) {
                ScoreChangeDetail detail = recalculateExamScore(examData, questionId, newVersionQuestion, gradingLevels, levelSplitArray);
                if (detail.isScoreChanged()) {
                    scoreChangedCount++;
                }
                detail.setRecalculationLog(recalcLog);
                changeDetailRepository.save(detail);
            }
            
            recalcLog.setAffectedExamCount(affectedCount);
            recalcLog.setScoreChangedExamCount(scoreChangedCount);
            recalcLog.setStatus(ScoreRecalculationLog.RecalculationStatus.COMPLETED);
            recalcLog.setCompletedAt(LocalDateTime.now());
            recalcLogRepository.save(recalcLog);
            
            logger.info("Score recalculation completed for question {}: {} exams affected, {} scores changed",
                    questionId, affectedCount, scoreChangedCount);
        } catch (Exception e) {
            recalcLog.setStatus(ScoreRecalculationLog.RecalculationStatus.FAILED);
            recalcLog.setErrorMessage(e.getMessage());
            recalcLog.setCompletedAt(LocalDateTime.now());
            recalcLogRepository.save(recalcLog);
            logger.error("Score recalculation failed for question {}: {}", questionId, e.getMessage(), e);
        }
    }
    
    private Question resolveNewVersionQuestion(String oldQuestionId, String triggerVersionId) {
        if (triggerVersionId != null) {
            return questionService.findById(triggerVersionId)
                    .orElseThrow(() -> new IllegalStateException("New version question not found: " + triggerVersionId));
        }
        Question oldQuestion = questionService.findById(oldQuestionId)
                .orElseThrow(() -> new IllegalStateException("Question not found: " + oldQuestionId));
        if (oldQuestion.getVersionStatus() == Question.VersionStatus.ACTIVE) {
            return oldQuestion;
        }
        if (oldQuestion.getVersionGroupId() != null) {
            List<Question> activeVersions = questionService.findByVersionGroupIdAndVersionStatus(
                    oldQuestion.getVersionGroupId(), Question.VersionStatus.ACTIVE);
            if (!activeVersions.isEmpty()) return activeVersions.getFirst();
        }
        throw new IllegalStateException("No active version found for question: " + oldQuestionId);
    }
    
    private ScoreChangeDetail recalculateExamScore(
            ExamData examData,
            String changedQuestionId,
            Question newVersionQuestion,
            List<GradingLevel> gradingLevels,
            Float[] levelSplitArray) {
        
        ExamResult oldResult = examData.getExamResult();
        float oldScore = oldResult != null ? oldResult.getScore() : 0f;
        String oldLevel = oldResult != null ? oldResult.getLevel() : null;
        String oldLevelId = oldResult != null ? oldResult.getLevelId() : null;
        
        // Build answers from raw data if available (produces proper Answer objects)
        Map<String, Object> rawAnswerData = examData.getRawAnswerData();
        if (rawAnswerData != null) {
            examData.checkAnswerMap(rawAnswerData);
        }
        
        // Rebuild the changed question's answer using the new version question
        if (newVersionQuestion != null) {
            rebuildAnswerWithNewVersion(examData, changedQuestionId, newVersionQuestion);
        }
        
        // Recalculate exam result from the updated answers
        recomputeExamResultFromAnswers(examData, gradingLevels, levelSplitArray);
        
        examDataService.save(examData);
        
        ExamResult newResult = examData.getExamResult();
        float newScore = newResult != null ? newResult.getScore() : 0f;
        String newLevel = newResult != null ? newResult.getLevel() : null;
        String newLevelId = newResult != null ? newResult.getLevelId() : null;
        
        return ScoreChangeDetail.builder()
                .id(UUIDv7.randomUUID().toString())
                .examDataId(examData.getId())
                .oldScore(oldScore)
                .newScore(newScore)
                .oldLevel(oldLevel)
                .newLevel(newLevel)
                .oldLevelId(oldLevelId)
                .newLevelId(newLevelId)
                .scoreChanged(Math.abs(oldScore - newScore) > 0.001f)
                .build();
    }
    
    private void rebuildAnswerWithNewVersion(ExamData examData, String changedQuestionId, Question newVersion) {
        Map<String, Answer<?, ?>> answersMap = examData.getAnswersMap();
        if (answersMap == null) return;
        
        Object oldAnswerObj = answersMap.get(changedQuestionId);
        if (oldAnswerObj == null) return;
        
        if (newVersion instanceof MultipleChoicesQuestion newMCQ) {
            rebuildMCQAnswer(answersMap, changedQuestionId, newMCQ, oldAnswerObj);
        } else if (newVersion instanceof QuestionGroup newQG) {
            rebuildQGAnswer(answersMap, changedQuestionId, newQG, oldAnswerObj);
        }
    }
    
    private void rebuildMCQAnswer(Map<String, Answer<?, ?>> answersMap, String changedQuestionId,
                                   MultipleChoicesQuestion newMCQ, Object oldAnswerObj) {
        // Load old MCQ to build position mapping for choice IDs
        Question oldQuestion = questionService.findById(changedQuestionId).orElse(null);
        if (!(oldQuestion instanceof MultipleChoicesQuestion oldMCQ)) return;
        
        Map<String, Integer> oldChoiceIdToPos = buildChoiceIdToPositionMap(oldMCQ);
        
        // Extract selected choice positions from old answer
        List<Integer> selectedPositions = extractSelectedPositions(oldAnswerObj, oldChoiceIdToPos);
        
        // Map positions to new choice IDs
        List<String> newChoiceIds = mapPositionsToNewChoiceIds(selectedPositions, newMCQ);
        
        var newAnswer = newMCQ.newAnswerFrom(newChoiceIds);
        newAnswer.check();
        answersMap.put(changedQuestionId, newAnswer);
    }
    
    private void rebuildQGAnswer(Map<String, Answer<?, ?>> answersMap, String changedQuestionId,
                                  QuestionGroup newQG, Object oldAnswerObj) {
        // Load old QG to build position mapping
        Question oldQuestion = questionService.findById(changedQuestionId).orElse(null);
        if (!(oldQuestion instanceof QuestionGroup oldQG)) return;
        
        List<Question> oldSubQuestions = oldQG.getQuestionLinks().stream()
                .sorted(Comparator.comparingInt(ToQuestionGroupLink::getOrderIndex))
                .map(link -> (Question) link.getSource())
                .toList();
        List<Question> newSubQuestions = newQG.getQuestionLinks().stream()
                .sorted(Comparator.comparingInt(ToQuestionGroupLink::getOrderIndex))
                .map(link -> (Question) link.getSource())
                .toList();
        
        if (oldSubQuestions.size() != newSubQuestions.size()) return;
        
        List<SingleQuestionAnswer> newSubAnswers = new ArrayList<>();
        
        if (oldAnswerObj instanceof QuestionGroupAnswer qgAnswer) {
            // Proper Answer object case (after checkAnswerMap)
            List<SingleQuestionAnswer> oldSubAnswers = qgAnswer.getAnswers();
            for (int i = 0; i < oldSubAnswers.size() && i < newSubQuestions.size(); i++) {
                Question newSubQ = newSubQuestions.get(i);
                Question oldSubQ = oldSubQuestions.get(i);
                if (newSubQ instanceof MultipleChoicesQuestion newSubMCQ
                        && oldSubQ instanceof MultipleChoicesQuestion oldSubMCQ
                        && oldSubAnswers.get(i) instanceof MultipleChoiceAnswer oldSubAnswer) {
                    Map<String, Integer> oldChoiceIdToPos = buildChoiceIdToPositionMap(oldSubMCQ);
                    List<Integer> selectedPositions = new ArrayList<>();
                    for (Choice selected : oldSubAnswer.getSelectedChoices()) {
                        Integer pos = oldChoiceIdToPos.get(selected.getId());
                        if (pos != null) selectedPositions.add(pos);
                    }
                    List<String> newChoiceIds = mapPositionsToNewChoiceIds(selectedPositions, newSubMCQ);
                    newSubAnswers.add(newSubMCQ.newAnswerFrom(newChoiceIds));
                }
            }
        } else if (oldAnswerObj instanceof Map<?, ?> map) {
            // Deserialized JSON case
            Object answersList = map.get("answers");
            if (answersList instanceof List<?> list) {
                for (int i = 0; i < list.size() && i < newSubQuestions.size(); i++) {
                    Question newSubQ = newSubQuestions.get(i);
                    Question oldSubQ = oldSubQuestions.get(i);
                    if (newSubQ instanceof MultipleChoicesQuestion newSubMCQ
                            && oldSubQ instanceof MultipleChoicesQuestion oldSubMCQ) {
                        Map<String, Integer> oldChoiceIdToPos = buildChoiceIdToPositionMap(oldSubMCQ);
                        List<Integer> selectedPositions = extractSelectedPositions(list.get(i), oldChoiceIdToPos);
                        List<String> newChoiceIds = mapPositionsToNewChoiceIds(selectedPositions, newSubMCQ);
                        newSubAnswers.add(newSubMCQ.newAnswerFrom(newChoiceIds));
                    }
                }
            }
        }
        
        var newQGAnswer = newQG.newAnswerFrom(newSubAnswers);
        newQGAnswer.check();
        answersMap.put(changedQuestionId, newQGAnswer);
    }
    
    private Map<String, Integer> buildChoiceIdToPositionMap(MultipleChoicesQuestion mcq) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < mcq.getChoices().size(); i++) {
            map.put(mcq.getChoices().get(i).getId(), i);
        }
        return map;
    }
    
    private List<Integer> extractSelectedPositions(Object answerObj, Map<String, Integer> oldChoiceIdToPos) {
        if (answerObj instanceof MultipleChoiceAnswer mcqAnswer) {
            List<Integer> positions = new ArrayList<>();
            for (Choice selected : mcqAnswer.getSelectedChoices()) {
                Integer pos = oldChoiceIdToPos.get(selected.getId());
                if (pos != null) positions.add(pos);
            }
            return positions;
        } else if (answerObj instanceof Map<?, ?> map) {
            // Deserialized JSON case
            Object selectedChoices = map.get("selectedChoices");
            if (selectedChoices instanceof List<?> list) {
                List<Integer> positions = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> choiceMap) {
                        Object id = choiceMap.get("id");
                        if (id instanceof String s) {
                            Integer pos = oldChoiceIdToPos.get(s);
                            if (pos != null) positions.add(pos);
                        }
                    }
                }
                return positions;
            }
        }
        return List.of();
    }
    
    private List<String> mapPositionsToNewChoiceIds(List<Integer> positions, MultipleChoicesQuestion newMCQ) {
        List<String> newChoiceIds = new ArrayList<>();
        for (int pos : positions) {
            if (pos < newMCQ.getChoices().size()) {
                newChoiceIds.add(newMCQ.getChoices().get(pos).getId());
            }
        }
        return newChoiceIds;
    }
    
    private void recomputeExamResultFromAnswers(
            ExamData examData,
            List<GradingLevel> gradingLevels,
            Float[] levelSplitArray) {
        
        Map<String, Answer<?, ?>> answersMap = examData.getAnswersMap();
        if (answersMap == null) return;
        
        int correctCount = 0;
        int halfCorrectCount = 0;
        int wrongCount = 0;
        float score = 0;
        
        for (Object answerObj : answersMap.values()) {
            if (!(answerObj instanceof Answer<?, ?> answer)) continue;
            var checkedResult = answer.check();
            switch (checkedResult.checkedResultType()) {
                case CORRECT -> correctCount++;
                case HALF_CORRECT -> halfCorrectCount++;
                case WRONG -> wrongCount++;
            }
            score += checkedResult.score();
        }
        
        ExamResult examResult = examData.getExamResult();
        if (examResult == null) {
            examResult = ExamResult.from(examData);
        }
        examResult.setCorrectCount(correctCount);
        examResult.setHalfCorrectCount(halfCorrectCount);
        examResult.setWrongCount(wrongCount);
        examResult.setScore((float) Math.round(score * 10) / 10);
        
        assignGradingLevel(examResult, gradingLevels, levelSplitArray, examData);
        examData.setExamResult(examResult);
    }
    
    private void assignGradingLevel(ExamResult examResult, List<GradingLevel> gradingLevels,
                                     Float[] levelSplitArray, ExamData examData) {
        int levelIndex;
        final int searchResult = Arrays.binarySearch(levelSplitArray, examResult.getScore());
        if (searchResult == levelSplitArray.length - 1) {
            levelIndex = levelSplitArray.length - 2;
        } else {
            if (searchResult >= 0) levelIndex = searchResult;
            else if (searchResult == -1) levelIndex = 0;
            else levelIndex = -searchResult - 2;
        }
        if (levelIndex < 0) levelIndex = 0;
        if (levelIndex >= gradingLevels.size()) levelIndex = gradingLevels.size() - 1;
        
        GradingLevel gradingLevel = gradingLevels.get(levelIndex);
        examResult.setLevel(gradingLevel.getName());
        examResult.setLevelId(gradingLevel.getId());
        examResult.setMessage(gradingLevel.getMessage());
        examResult.setColorHex(gradingLevel.getColorHex());
        examResult.setShowCreatingAccountGuide(gradingLevel.getCreatingUserStrategy() != GradingLevel.CreatingUserStrategy.NOT_CREATE);
    }
    
    @SuppressWarnings("unchecked")
    private Float[] buildLevelSplitArray() {
        ArrayList<Number> levelSplit = settingService.getItem("grading", "splits").getValue(ArrayList.class);
        Float[] levelSplitArray = new Float[levelSplit.size() + 1];
        AtomicInteger index = new AtomicInteger(0);
        levelSplit.forEach(splitScore -> {
            levelSplitArray[index.get()] = splitScore.floatValue();
            index.incrementAndGet();
        });
        
        SettingItem scoreSettingItem = settingService.getItem("generating", "questionScore");
        float singleQuestionScore = scoreSettingItem.getValue(Number.class).floatValue();
        // Use a reasonable max score - can't know exact exam question count here
        levelSplitArray[levelSplitArray.length - 1] = 1000f;
        return levelSplitArray;
    }
}
