package indi.etern.checkIn.service.dao;

import indi.etern.checkIn.entities.exam.ExamData;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.setting.SettingItem;
import indi.etern.checkIn.entities.setting.grading.GradingLevel;
import indi.etern.checkIn.repositories.ExamDataRepository;
import indi.etern.checkIn.service.exam.ExamGenerator;
import indi.etern.checkIn.service.exam.ExamResult;
import indi.etern.checkIn.service.web.SubmitFrequencyMonitorService;
import indi.etern.checkIn.throwable.entity.SettingInvalidException;
import indi.etern.checkIn.throwable.exam.ExamException;
import indi.etern.checkIn.throwable.exam.ExamSubmittedException;
import indi.etern.checkIn.throwable.exam.grading.ExamInvalidException;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ExamDataService {
    public static volatile ExamDataService singletonInstance;
    final SettingService settingService;
    final GradingLevelService gradingLevelService;
    @Resource
    ExamDataRepository examDataRepository;
    private final QuestionService questionService;
    private final Logger logger = LoggerFactory.getLogger(ExamDataService.class);
    @Lazy
    private final ExamGenerator examGenerator;
    private final QuestionStatisticService questionStatisticService;
    private final indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService thirdPartyApiWebSocketService;
    private final SubmitFrequencyMonitorService submitFrequencyMonitorService;
    private final AnswerLimitService answerLimitService;
    
    protected ExamDataService(SettingService settingService, GradingLevelService gradingLevelService, QuestionService questionService, @Lazy ExamGenerator examGenerator, QuestionStatisticService questionStatisticService, @Lazy indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService thirdPartyApiWebSocketService, SubmitFrequencyMonitorService submitFrequencyMonitorService, AnswerLimitService answerLimitService) {
        singletonInstance = this;
        this.settingService = settingService;
        this.gradingLevelService = gradingLevelService;
        this.questionService = questionService;
        this.examGenerator = examGenerator;
        this.questionStatisticService = questionStatisticService;
        this.thirdPartyApiWebSocketService = thirdPartyApiWebSocketService;
        this.submitFrequencyMonitorService = submitFrequencyMonitorService;
        this.answerLimitService = answerLimitService;
    }
    
    public void save(ExamData examData) {
        examDataRepository.save(examData);
    }
    
    public Optional<ExamData> findById(String id) {
        return examDataRepository.findById(id);
    }
    
    @Transactional(noRollbackFor = Throwable.class)
    public ExamResult handleSubmit(ExamData examData, Map<String, Object> answer) throws ExamException {
        if (examData.getStatus() != ExamData.Status.ONGOING) {
            throw new ExamInvalidException();
        }
        //noinspection unchecked
        ArrayList<Number> levelSplit = settingService.getItem("grading", "splits").getValue(ArrayList.class);
        List<GradingLevel> gradingLevels = gradingLevelService.findAll();
        if (gradingLevels.isEmpty()) {
            throw new SettingInvalidException("grading levels missing");
        }
        
        Float[] levelSplitArray = new Float[levelSplit.size() + 1];
        AtomicInteger index = new AtomicInteger(0);
        levelSplit.forEach(splitScore -> {
            levelSplitArray[index.get()] = splitScore.floatValue();
            index.incrementAndGet();
        });
        
        SettingItem scoreSettingItem = settingService.getItem("generating", "questionScore");
        float singleQuestionScore = scoreSettingItem.getValue(Number.class).floatValue();
        
        levelSplitArray[levelSplitArray.length - 1] = examData.getQuestionAmount() * singleQuestionScore;
        
        final ExamResult examResult = examData.checkAnswerMap(answer);
        final int searchResult = Arrays.binarySearch(levelSplitArray, examResult.getScore());
        int levelIndex;
        if (searchResult == levelSplitArray.length - 1) {//max score
            levelIndex = levelSplitArray.length - 2;//avoid overflow
        } else {
            if (searchResult >= 0) levelIndex = searchResult;//at lower point
            else if (searchResult == -1) levelIndex = 0;//at lower point
            else levelIndex = -searchResult - 2;//at part
        }
        final GradingLevel gradingLevel = gradingLevels.get(levelIndex);
        examResult.setLevel(gradingLevel.getName());
        examResult.setLevelId(gradingLevel.getId());
        examResult.setMessage(gradingLevel.getMessage());
        examResult.setColorHex(gradingLevel.getColorHex());
        examResult.setShowCreatingAccountGuide(gradingLevel.getCreatingUserStrategy() != GradingLevel.CreatingUserStrategy.NOT_CREATE);
        
        LocalDateTime now = LocalDateTime.now();
        examData.setExamResult(examResult);
        examData.setSubmitTime(now);
        examData.setStatus(ExamData.Status.SUBMITTED);
        examDataRepository.save(examData);
        
        // 记录提交行为，用于监控短时间内多次提交
        submitFrequencyMonitorService.recordSubmit(examData.getQqNumber(), examData.getId());
        
        // 发送提交考试结果通知
        try {
            SettingItem paperSubmitEnabled = settingService.getItem("thirdPartyApi.notification", "paperSubmit.enabled");
            if (paperSubmitEnabled.getValue(Boolean.class)) {
                Map<String, Object> data = new HashMap<>();
                data.put("qq", String.valueOf(examData.getQqNumber()));
                data.put("paper_id", examData.getId());
                data.put("rating_id", examResult.getLevelId());
                data.put("score", examResult.getScore());
                data.put("generate_time", new int[]{examData.getGenerateTime().getYear(), examData.getGenerateTime().getMonthValue(), examData.getGenerateTime().getDayOfMonth(), examData.getGenerateTime().getHour(), examData.getGenerateTime().getMinute(), examData.getGenerateTime().getSecond(), examData.getGenerateTime().getNano()});
                data.put("submit_time", new int[]{now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano()});
                data.put("max_answer_count", answerLimitService.getMaxAnswerCount());
                data.put("answer_count", answerLimitService.getAnswerCount(String.valueOf(examData.getQqNumber())));
                thirdPartyApiWebSocketService.sendNotification("notification_paper_submit", data);
                logger.info("Sent paper_submit notification for exam {}", examData.getId());
            } else {
                logger.debug("paperSubmit notification is disabled");
            }
        } catch (Exception e) {
            logger.error("Failed to send paper submit notification", e);
        }
        
        // 检查是否需要发送短时间提交试题的通知
        try {
            SettingItem quickSubmitEnabled = settingService.getItem("thirdPartyApi.notification", "quickSubmit.enabled");
            if (quickSubmitEnabled.getValue(Boolean.class)) {
                SettingItem quickSubmitThreshold = settingService.getItem("thirdPartyApi.notification", "quickSubmit.threshold");
                int thresholdMinutes = quickSubmitThreshold.getValue(Integer.class);
                long intervalSeconds = java.time.Duration.between(examData.getGenerateTime(), now).getSeconds();
                if (intervalSeconds <= thresholdMinutes * 60) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("qq", String.valueOf(examData.getQqNumber()));
                    data.put("generate_time", new int[]{examData.getGenerateTime().getYear(), examData.getGenerateTime().getMonthValue(), examData.getGenerateTime().getDayOfMonth(), examData.getGenerateTime().getHour(), examData.getGenerateTime().getMinute(), examData.getGenerateTime().getSecond(), examData.getGenerateTime().getNano()});
                    data.put("submit_time", new int[]{now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano()});
                    data.put("interval", intervalSeconds);
                    thirdPartyApiWebSocketService.sendNotification("notification_quick_submit", data);
                    logger.info("Sent quick_submit notification for exam {} (interval: {} seconds)", examData.getId(), intervalSeconds);
                } else {
                    logger.debug("quickSubmit notification not sent: interval {} seconds > threshold {} minutes", intervalSeconds, thresholdMinutes);
                }
            } else {
                logger.debug("quickSubmit notification is disabled");
            }
        } catch (Exception e) {
            logger.error("Failed to send quick submit notification", e);
        }
        
        return examResult;
    }
    
    public List<ExamData> findAllBySubmitTimeBetween(LocalDate from, LocalDate to) {
        return examDataRepository.findAllBySubmitTimeBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }
    
    public List<ExamData> findAllByGenerateTimeBetween(LocalDate from, LocalDate to) {
        return examDataRepository.findAllByGenerateTimeBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }
    
    public Map<String, Object> getExamDataQuestions(int[] indexes, ExamData examData) throws ExamException {
        if (examData.getStatus() == ExamData.Status.ONGOING) {
            Map<String, Object> result = new HashMap<>();
            List<String> allQuestionIds = examData.getQuestionIds();
            List<String> questionIds = new ArrayList<>();
            for (int index : indexes) {
                questionIds.add(allQuestionIds.get(index));
            }
            final List<Question> questions = new ArrayList<>(questionService.findAllById(questionIds));
            if (questions.size() != indexes.length) {
                logger.warn("some questions of the exam is missing, remediate exam");
                ExamData examData1 = examGenerator.remediateExam(examData);
                examDataRepository.save(examData1);
                questionStatisticService.appendStatistic(examData);
                return getExamDataQuestions(indexes, examData);
//            throw new RuntimeException("Questions not found");
            }
            Map<Integer, Question> indexQuestionMap = new HashMap<>();
            questions.forEach(question -> indexQuestionMap.put(allQuestionIds.indexOf(question.getId()), question));
            result.put("questions", indexQuestionMap);
            return result;
        } else if (examData.getStatus() == ExamData.Status.SUBMITTED) {
            logger.debug("Exam[{}] already submitted", examData.getId());
            throw new ExamSubmittedException();
        } else {
            logger.error("Exam[{}] invalided", examData.getId());
            throw new ExamInvalidException();
        }
    }
    
    public Optional<ExamData> findMaxByQQ(long qq) {
        List<ExamData> examDataList = examDataRepository.findAllByQqNumberIs(qq);
        return examDataList.stream().max(Comparator.comparing(examData -> examData.getExamResult() == null ? ExamResult.EMPTY :examData.getExamResult()));
    }
    
    public void invalidAllByQQ(long qq) {
        List<ExamData> examDataList = examDataRepository.findAllByQqNumberAndStatus(qq, ExamData.Status.ONGOING);
        for (ExamData examData : examDataList) {
            examData.setStatus(ExamData.Status.EXPIRED);
        }
        examDataRepository.saveAll(examDataList);
    }
    
    public List<ExamData> findAllByQQ(long qq) {
        return examDataRepository.findAllByQqNumberIs(qq);
    }
    
    public List<ExamData> getExamDataContainsQuestionById(String questionId) {
        return examDataRepository.findAllByQuestionIdsContains(questionId);
//        return null;
    }
}