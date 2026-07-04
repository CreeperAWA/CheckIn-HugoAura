package indi.etern.checkIn.service.question;

import indi.etern.checkIn.entities.exam.ExamData;
import indi.etern.checkIn.entities.question.impl.MultipleChoicesQuestion;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.impl.QuestionGroup;
import indi.etern.checkIn.entities.question.interfaces.answer.Answer;
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
    private final Logger logger = LoggerFactory.getLogger(ScoreRecalculationService.class);
    
    public ScoreRecalculationService(ExamDataService examDataService,
                                     QuestionService questionService,
                                     GradingLevelService gradingLevelService,
                                     ScoreRecalculationLogRepository recalcLogRepository,
                                     ScoreChangeDetailRepository changeDetailRepository,
                                     SettingService settingService,
                                     ApplicationContext applicationContext) {
        this.examDataService = examDataService;
        this.questionService = questionService;
        this.gradingLevelService = gradingLevelService;
        this.recalcLogRepository = recalcLogRepository;
        this.changeDetailRepository = changeDetailRepository;
        this.settingService = settingService;
        this.applicationContext = applicationContext;
    }
    
    public String triggerAsyncRecalculation(String questionId, Long triggeredByQq) {
        ScoreRecalculationLog log = ScoreRecalculationLog.builder()
                .id(UUIDv7.randomUUID().toString())
                .questionId(questionId)
                .triggerType(ScoreRecalculationLog.TriggerType.ANSWER_KEY_CHANGE)
                .triggeredAt(LocalDateTime.now())
                .triggeredByQq(triggeredByQq)
                .status(ScoreRecalculationLog.RecalculationStatus.PENDING)
                .build();
        recalcLogRepository.save(log);
        
        applicationContext.publishEvent(new ScoreRecalculationEvent(questionId, log.getId(), triggeredByQq));
        return log.getId();
    }
    
    public String triggerManualRecalculation(String questionId, Long triggeredByQq) {
        ScoreRecalculationLog log = ScoreRecalculationLog.builder()
                .id(UUIDv7.randomUUID().toString())
                .questionId(questionId)
                .triggerType(ScoreRecalculationLog.TriggerType.MANUAL)
                .triggeredAt(LocalDateTime.now())
                .triggeredByQq(triggeredByQq)
                .status(ScoreRecalculationLog.RecalculationStatus.PENDING)
                .build();
        recalcLogRepository.save(log);
        
        applicationContext.publishEvent(new ScoreRecalculationEvent(questionId, log.getId(), triggeredByQq));
        return log.getId();
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeRecalculation(String questionId, String logId) {
        ScoreRecalculationLog recalcLog = recalcLogRepository.findById(logId).orElseThrow();
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
            
            for (ExamData examData : affectedExams) {
                ScoreChangeDetail detail = recalculateExamScore(examData, questionId, gradingLevels, levelSplitArray);
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
    
    private ScoreChangeDetail recalculateExamScore(
            ExamData examData,
            String changedQuestionId,
            List<GradingLevel> gradingLevels,
            Float[] levelSplitArray) {
        
        ExamResult oldResult = examData.getExamResult();
        float oldScore = oldResult != null ? oldResult.getScore() : 0f;
        String oldLevel = oldResult != null ? oldResult.getLevel() : null;
        String oldLevelId = oldResult != null ? oldResult.getLevelId() : null;
        
        // Try to recalculate using raw answer data for reliability
        Map<String, Object> rawAnswerData = examData.getRawAnswerData();
        if (rawAnswerData != null) {
            ExamResult newExamResult = examData.checkAnswerMap(rawAnswerData);
            assignGradingLevel(newExamResult, gradingLevels, levelSplitArray, examData);
            examData.setExamResult(newExamResult);
        } else {
            rebuildSingleAnswerInExam(examData, changedQuestionId);
            recomputeExamResultFromAnswers(examData, gradingLevels, levelSplitArray);
        }
        
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
    
    @SuppressWarnings("unchecked")
    private void rebuildSingleAnswerInExam(ExamData examData, String changedQuestionId) {
        Map<String, Answer<?, ?>> answersMap = examData.getAnswersMap();
        if (answersMap == null) return;
        
        // Extract choice IDs from the deserialized answer JSON
        Object answerObj = answersMap.get(changedQuestionId);
        if (answerObj == null) return;
        
        List<String> choiceIds = extractChoiceIdsFromAnswer(answerObj);
        
        Optional<Question> questionOpt = questionService.findById(changedQuestionId);
        if (questionOpt.isPresent() && questionOpt.get() instanceof MultipleChoicesQuestion mcq) {
            var newAnswer = mcq.newAnswerFrom(choiceIds);
            newAnswer.check();
            answersMap.put(changedQuestionId, newAnswer);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<String> extractChoiceIdsFromAnswer(Object answerObj) {
        if (answerObj instanceof Map<?, ?> map) {
            Object selectedChoices = map.get("selectedChoices");
            if (selectedChoices instanceof List<?> list) {
                List<String> ids = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> choiceMap) {
                        Object id = choiceMap.get("id");
                        if (id instanceof String s) {
                            ids.add(s);
                        }
                    }
                }
                return ids;
            }
        }
        return List.of();
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
        
        for (Answer<?, ?> answer : answersMap.values()) {
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
