package indi.etern.checkIn.controller.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.action.ActionExecutor;
import indi.etern.checkIn.action.interfaces.ResultContext;
import indi.etern.checkIn.action.oauth2.GetOAuth2ProvidersSimpleInfoAction;
import indi.etern.checkIn.action.partition.GetPartitionsAction;
import indi.etern.checkIn.action.setting.get.GetFacadeSetting;
import indi.etern.checkIn.action.setting.get.GetGradingSetting;
import indi.etern.checkIn.auth.JwtTokenProvider;
import indi.etern.checkIn.entities.exam.ExamData;
import indi.etern.checkIn.entities.question.impl.Partition;
import indi.etern.checkIn.entities.setting.SettingItem;
import indi.etern.checkIn.entities.setting.grading.GradingLevel;
import indi.etern.checkIn.entities.setting.oauth2.OAuth2ProviderInfo;
import indi.etern.checkIn.entities.user.User;
import indi.etern.checkIn.service.dao.*;
import indi.etern.checkIn.service.exam.ExamGenerator;
import indi.etern.checkIn.service.exam.ExamResult;
import indi.etern.checkIn.service.exam.SignUpCompletingType;
import indi.etern.checkIn.service.web.OAuth2Service;
import indi.etern.checkIn.service.web.TurnstileService;
import indi.etern.checkIn.throwable.entity.UserExistsException;
import indi.etern.checkIn.throwable.exam.ExamException;
import indi.etern.checkIn.throwable.exam.ExamIllegalStateException;
import indi.etern.checkIn.throwable.exam.ExamSubmittedException;
import indi.etern.checkIn.throwable.exam.generate.ExamGenerateFailedException;
import indi.etern.checkIn.throwable.exam.generate.PartitionsOutOfRangeException;
import indi.etern.checkIn.throwable.exam.grading.ExamInvalidException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class ExamController {
    private final PartitionService partitionService;
    private final ActionExecutor actionExecutor;
    private final ExamGenerator examGenerator;
    private final ExamDataService examDataService;
    private final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(ExamController.class);
    private final String TOKEN_REFRESHED_SUCCESSFULLY_JSON = "{\"type\":\"success\",\"message\":\"Token refreshed successfully\"}";
    private final String MISSING_REQUIRED_BINDING = "{\"type\":\"error\",\"message\":\"Missing required OAuth2 binding\", \"description\": \"未绑定要求的第三方账户\"}";
    private final String EXAM_IS_NOT_EXIST_JSON = "{\"type\":\"error\",\"message\":\"Exam is not exist\"}";
    private final String EXAM_INVALIDED_JSON = "{\"type\":\"error\",\"message\":\"Exam invalided\"}";
    private final String EXAM_SUBMITTED_JSON = "{\"type\":\"error\",\"message\":\"Exam has already submitted\"}";
    private final String EXAM_NOT_SUBMITTED_JSON = "{\"type\":\"error\",\"message\":\"Exam has not been submitted\"}";
    private final String USER_EXISTS_JSON = "{\"type\":\"error\",\"message\":\"User already exists\"}";
    private final String NOT_SUPPORTED_LEVEL_FOR_SIGN_UP_JSON = "{\"type\":\"error\",\"message\":\"Not a supported level for sign up\"}";
    private final QuestionStatisticService questionStatisticService;
    private final SettingService settingService;
    private final UserService userService;
    private final GradingLevelService gradingLevelService;
    private final TurnstileService turnstileService;
    private final OAuth2Service oAuth2Service;
    private final JwtTokenProvider jwtTokenProvider;

    public ExamController(PartitionService partitionService, ActionExecutor actionExecutor, ExamGenerator examGenerator,
                          ExamDataService examDataService, ObjectMapper objectMapper, QuestionStatisticService questionStatisticService,
                          SettingService settingService, UserService userService, GradingLevelService gradingLevelService,
                          TurnstileService turnstileService, OAuth2Service oAuth2Service, JwtTokenProvider jwtTokenProvider) {
        this.partitionService = partitionService;
        this.actionExecutor = actionExecutor;
        this.examGenerator = examGenerator;
        this.examDataService = examDataService;
        this.objectMapper = objectMapper;
        this.questionStatisticService = questionStatisticService;
        this.settingService = settingService;
        this.userService = userService;
        this.gradingLevelService = gradingLevelService;
        this.turnstileService = turnstileService;
        this.oAuth2Service = oAuth2Service;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping(path = "/api/generate")
    @Transactional(noRollbackFor = Throwable.class)
    public String generateExam(@RequestBody GenerateRequest generateRequest, HttpServletRequest httpServletRequest) throws JsonProcessingException {
        Cookie examTokenCookie = Arrays.stream(httpServletRequest.getCookies())
                .filter(c -> c.getName().equals("examToken")).findFirst().orElseThrow(IllegalStateException::new);
        String examToken = examTokenCookie.getValue();
        Jws<Claims> claimsJws = jwtTokenProvider.parseToken(examToken);
        Map<String, String> examOAuth2Map = null;
        if (claimsJws != null && claimsJws.getHeader().get("OAuth2") instanceof Map<?,?> map) {
            //noinspection unchecked
            examOAuth2Map = (Map<String, String>) map;
        }
        List<OAuth2ProviderInfo> providerInfos = oAuth2Service.getProviderInfos().stream()
                .filter(o -> o.getExamLoginMode() == OAuth2ProviderInfo.ExamLoginMode.REQUIRED).toList();
        if (!providerInfos.isEmpty()) {
            if (examOAuth2Map == null) {
                return MISSING_REQUIRED_BINDING;
            } else {
                for (OAuth2ProviderInfo providerInfo : providerInfos) {
                    if (!examOAuth2Map.containsKey(providerInfo.getId())) {
                        return MISSING_REQUIRED_BINDING;
                    }
                }
            }
        }

        if (turnstileService.isTurnstileEnabledOnExam() && turnstileService.isServiceEnable()) {
            try {
                turnstileService.check(generateRequest.turnstileToken, httpServletRequest);
            } catch (Exception e) {
                Map<String, String> errorDataMap = new HashMap<>();
                errorDataMap.put("type", "error");
                errorDataMap.put("description", e.getMessage());
                errorDataMap.put("exceptionType", e.getClass().getSimpleName());
                return objectMapper.writeValueAsString(errorDataMap);
            }
        }
        try {
            List<Integer> range = null;
            try {
                SettingItem item = settingService.getItem("generating", "partitionRange");
                final ArrayList<?> value = item.getValue(ArrayList.class);
                if (value.size() == 2) {
                    //noinspection unchecked
                    range = (List<Integer>) value;
                } else {
                    logger.warn("Setting \"generating.partitionRange\" not match to 2 elements");
                }
            } catch (Exception ignored) {
                logger.warn("Setting \"generating.partitionRange\" missing");
            }
            final int size = generateRequest.partitionIds.size();
            if (range == null || (size >= range.getFirst() && size <= range.getLast())) {
                final ExamData examData = examGenerator.generateExam(generateRequest.qq, partitionService.findAllByIds(generateRequest.partitionIds));
                examDataService.invalidAllByQQ(generateRequest.qq);
                examData.setOAuth2Bindings(examOAuth2Map);
                examDataService.save(examData);
                questionStatisticService.appendStatistic(examData);
                Map<String, Object> result = new HashMap<>();
                result.put("examId", examData.getId());
                result.put("questionItemCount", examData.getQuestionIds().size());
                examData.sendUpdateExamRecord();
                return objectMapper.writeValueAsString(result);
            } else {
                throw new PartitionsOutOfRangeException();
            }
        } catch (ExamGenerateFailedException e) {
            Map<String, String> errorDataMap = new HashMap<>();
            errorDataMap.put("type", "error");
            errorDataMap.put("description", e.getDescription());
            errorDataMap.put("exceptionType", e.getClass().getSimpleName());
            return objectMapper.writeValueAsString(errorDataMap);
        }
    }

    @RequestMapping(method = RequestMethod.POST, path = "/api/exam-questions", produces = "application/json;charset=UTF-8")
    @Transactional(propagation = Propagation.NESTED)
    public String getQuestionsByExamIdAndIndexes(@RequestBody GetQuestionsByIndexRequest request) throws JsonProcessingException, ExamException {
        Optional<ExamData> optionalExamData = examDataService.findById(request.examId);
        if (optionalExamData.isPresent()) {
            try {
                final Map<String, Object> result = examDataService.getExamDataQuestions(request.indexes, optionalExamData.get());
                return objectMapper.writeValueAsString(result);
            } catch (ExamInvalidException e) {
                return EXAM_INVALIDED_JSON;
            } catch (ExamSubmittedException e) {
                return EXAM_SUBMITTED_JSON;
            }
        } else {
            return EXAM_IS_NOT_EXIST_JSON;
        }
    }

    @SneakyThrows
    @Transactional
    @RequestMapping(method = RequestMethod.POST, path = "/api/get-result")
    public String getResult(@RequestBody GetResultRequest getResultRequest) {
        Optional<ExamData> optionalExamData = examDataService.findById(getResultRequest.examId);
        if (optionalExamData.isPresent()) {
            final ExamData examData = optionalExamData.get();
            final ExamResult examResult = examData.getExamResult();
            if (examResult != null) {
                return objectMapper.writeValueAsString(examResult);
            } else {
                return EXAM_NOT_SUBMITTED_JSON;
            }
        } else {
            return EXAM_IS_NOT_EXIST_JSON;
        }
    }

    @SneakyThrows
    @Transactional
    @RequestMapping(method = RequestMethod.POST, path = "/api/submit")
    public String submit(@RequestBody SubmitRequest submitRequest) {
        Optional<ExamData> optionalExamData = examDataService.findById(submitRequest.examId);
        if (optionalExamData.isPresent()) {
            final ExamData examData = optionalExamData.get();
            final ExamResult examResult1 = examData.getExamResult();
            if (examResult1 != null) {
                return EXAM_SUBMITTED_JSON;
            } else {
                try {
                    final ExamResult examResult = examDataService.handleSubmit(examData, submitRequest.answer);
                    if (userService.existsByQQNumber(examResult.getQq())) {
                        examResult.setSignUpCompletingType(SignUpCompletingType.USER_EXISTS);
                    } else {
                        examResult.setSignUpCompletingType(SignUpCompletingType.INCOMPLETED);
                    }
                    examData.sendUpdateExamRecord();
                    questionStatisticService.appendStatistic(examData);
                    return objectMapper.writeValueAsString(examResult);
                } catch (ExamInvalidException e) {
                    logger.error("ExamController[{}] invalided", examData.getId());
                    return EXAM_INVALIDED_JSON;
                }
            }
        } else {
            logger.error("Could not found examData({})", submitRequest.examId);
            return EXAM_IS_NOT_EXIST_JSON;
        }
    }

    @Transactional
    @RequestMapping(method = RequestMethod.GET, path = "/api/exam-data")
    public Map<String, Object> getData() {
        return getPreExamData();
    }

    @Transactional
    @RequestMapping(method = RequestMethod.GET, path = "/api/pre-exam-data")
    public Map<String, Object> getPreExamData() {
        Map<String, Object> result = new HashMap<>();
        var facadeSettingContext = actionExecutor.execute(GetFacadeSetting.class);
        var gradingSettingContext = actionExecutor.execute(GetGradingSetting.class);

        final Boolean showRequiredPartitions = settingService.getItem("generating", "showRequiredPartitions").getValue(Boolean.class);
        //noinspection unchecked
        final List<String> requiredPartitionIds = settingService.getItem("generating", "requiredPartitions").getValue(ArrayList.class);
        Set<String> requiredPartitionIdSet = new HashSet<>(requiredPartitionIds);
        List<String> selectablePartitionIds = new ArrayList<>();

        ResultContext<GetPartitionsAction.Output> context = actionExecutor.execute(GetPartitionsAction.class);
        final Map<String, String> usedPartitionsNameMap = getPartitionsNameMap(context, requiredPartitionIdSet, selectablePartitionIds);

        final GetFacadeSetting.SuccessOutput output = facadeSettingContext.getOutput();
        result.put("facadeData", output.data());
//        result.put("generatingData", generatingSettingMap);
        result.put("gradingData", filterGradingDataForPreExam(gradingSettingContext.getOutput().data()));

        Map<String, Object> extraData = output.extraData();
        extraData.put("partitions", usedPartitionsNameMap);
        var resultContext = actionExecutor.execute(GetOAuth2ProvidersSimpleInfoAction.class);
        var oAuth2ProviderInfos = resultContext.getOutput().providerInfos().stream()
                .filter(o -> o.getExamLoginMode() != OAuth2ProviderInfo.ExamLoginMode.DISABLED)
                .map(ProviderItem::from).toList();
        extraData.put("oAuth2Providers", oAuth2ProviderInfos);
        extraData.put("selectablePartitionIds", selectablePartitionIds);
        if (showRequiredPartitions)
            extraData.put("requiredPartitionIds", requiredPartitionIds);
        result.put("extraData", extraData);
        return result;
    }

    @Transactional
    @RequestMapping(method = RequestMethod.GET, path = "/api/post-exam-data")
    public Map<String, Object> getPostExamData(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        Optional<Cookie> examTokenCookieOptional = Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals("examToken"))
                .findFirst();
        
        if (examTokenCookieOptional.isEmpty()) {
            result.put("error", "Missing exam token");
            result.put("passed", false);
            return result;
        }
        
        String examToken = examTokenCookieOptional.get().getValue();
        try {
            Jws<Claims> claimsJws = JwtTokenProvider.singletonInstance.parseToken(examToken);
            Claims body = claimsJws.getBody();
            String qqNumberStr = body.getSubject();
            
            if (qqNumberStr == null || qqNumberStr.isEmpty()) {
                result.put("error", "Invalid token");
                result.put("passed", false);
                return result;
            }
            
            long qqNumber = Long.parseLong(qqNumberStr);
            
            List<ExamData> userExamDataList = examDataService.findAllByQQ(qqNumber);
            if (userExamDataList.isEmpty()) {
                result.put("error", "No exam records found");
                result.put("passed", false);
                return result;
            }
            
            Optional<ExamData> bestExamOpt = userExamDataList.stream()
                    .filter(ed -> ed.getExamResult() != null)
                    .filter(ed -> ed.getStatus() == ExamData.Status.SUBMITTED || ed.getStatus() == ExamData.Status.SIGN_UP_COMPLETED)
                    .max(Comparator.comparing(ed -> ed.getExamResult().getScore()));
            
            if (bestExamOpt.isEmpty()) {
                result.put("error", "No completed exam found");
                result.put("passed", false);
                return result;
            }
            
            ExamData bestExam = bestExamOpt.get();
            ExamResult examResult = bestExam.getExamResult();
            
            result.put("passed", true);
            result.put("examDataId", bestExam.getId());
            result.put("qq", examResult.getQq());
            result.put("score", examResult.getScore());
            result.put("correctCount", examResult.getCorrectCount());
            result.put("halfCorrectCount", examResult.getHalfCorrectCount());
            result.put("wrongCount", examResult.getWrongCount());
            result.put("questionCount", examResult.getQuestionCount());
            result.put("level", examResult.getLevel());
            result.put("levelId", examResult.getLevelId());
            result.put("colorHex", examResult.getColorHex());
            result.put("message", examResult.getMessage());
            result.put("signUpCompletingType", examResult.getSignUpCompletingType());
            result.put("showCreatingAccountGuide", examResult.isShowCreatingAccountGuide());
            
            addPreExamDataToResult(result);
            
        } catch (Exception e) {
            logger.error("Failed to parse exam token or get exam data", e);
            result.put("error", "Invalid token or server error");
            result.put("passed", false);
        }
        
        return result;
    }
    
    private void addPreExamDataToResult(Map<String, Object> result) {
        var facadeSettingContext = actionExecutor.execute(GetFacadeSetting.class);
        var gradingSettingContext = actionExecutor.execute(GetGradingSetting.class);
        
        final Boolean showRequiredPartitions = settingService.getItem("generating", "showRequiredPartitions").getValue(Boolean.class);
        final List<String> requiredPartitionIds = settingService.getItem("generating", "requiredPartitions").getValue(ArrayList.class);
        Set<String> requiredPartitionIdSet = new HashSet<>(requiredPartitionIds);
        List<String> selectablePartitionIds = new ArrayList<>();
        
        ResultContext<GetPartitionsAction.Output> context = actionExecutor.execute(GetPartitionsAction.class);
        final Map<String, String> usedPartitionsNameMap = getPartitionsNameMap(context, requiredPartitionIdSet, selectablePartitionIds);
        
        final GetFacadeSetting.SuccessOutput output = facadeSettingContext.getOutput();
        result.put("facadeData", output.data());
        
        Map<String, Object> gradingData = gradingSettingContext.getOutput().data();
        result.put("gradingData", filterGradingDataForPreExam(gradingData));
        
        Map<String, Object> extraData = output.extraData();
        extraData.put("partitions", usedPartitionsNameMap);
        var resultContext = actionExecutor.execute(GetOAuth2ProvidersSimpleInfoAction.class);
        var oAuth2ProviderInfos = resultContext.getOutput().providerInfos().stream()
                .filter(o -> o.getExamLoginMode() != OAuth2ProviderInfo.ExamLoginMode.DISABLED)
                .map(ProviderItem::from).toList();
        extraData.put("oAuth2Providers", oAuth2ProviderInfos);
        extraData.put("selectablePartitionIds", selectablePartitionIds);
        if (showRequiredPartitions)
            extraData.put("requiredPartitionIds", requiredPartitionIds);
        result.put("extraData", extraData);
        result.put("partitions", usedPartitionsNameMap);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> filterGradingDataForPreExam(Map<String, Object> gradingData) {
        Map<String, Object> filteredData = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : gradingData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if ("levels".equals(key) && value instanceof List) {
                List<GradingLevel> levels = (List<GradingLevel>) value;
                List<Map<String, Object>> filteredLevels = new ArrayList<>();
                
                for (GradingLevel level : levels) {
                    Map<String, Object> levelMap = new HashMap<>();
                    levelMap.put("id", level.getId());
                    levelMap.put("name", level.getName());
                    levelMap.put("colorHex", level.getColorHex());
                    levelMap.put("creatingUserStrategy", level.getCreatingUserStrategy());
                    levelMap.put("levelIndex", level.getLevelIndex());
                    levelMap.put("creatingUserRole", level.getCreatingUserRole());
                    // 不添加 description 和 message
                    filteredLevels.add(levelMap);
                }
                
                filteredData.put(key, filteredLevels);
            } else {
                filteredData.put(key, value);
            }
        }
        
        return filteredData;
    }

    private Map<String, String> getPartitionsNameMap(ResultContext<GetPartitionsAction.Output> context, Set<String> requiredPartitionIdSet, List<String> selectablePartitionIds) {
        List<Partition> partitions = context.getOutput().partitions();
        Map<String, String> usedPartitionsNameMap = new HashMap<>();
        partitions.forEach((partition) -> {
            if (!requiredPartitionIdSet.contains(partition.getId())) {
                if (partition.getEnabledQuestionCount() != 0) {
                    selectablePartitionIds.add(partition.getId());
                    usedPartitionsNameMap.put(partition.getId(), partition.getName());
                }
            } else {
                usedPartitionsNameMap.put(partition.getId(), partition.getName());
            }
        });
        return usedPartitionsNameMap;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/api/refresh-exam-token")
    public String refreshExamToken(@RequestBody RefreshTokenRequest refreshTokenRequest, HttpServletRequest request, HttpServletResponse response) {
        List<String> unbindOAuth2s = refreshTokenRequest.unbindOAuth2s;
        Optional<Cookie> previousTokenCookieOptional = Arrays.stream(request.getCookies()).filter(c -> c.getName().equals("examToken")).findFirst();
        String previousExamToken = previousTokenCookieOptional.map(Cookie::getValue).orElse(null);
        User anonymous = User.ANONYMOUS;
        Jws<Claims> claimsJws;
        if (previousExamToken != null) {
            try {
                claimsJws = JwtTokenProvider.singletonInstance.parseToken(previousExamToken);
            } catch (SignatureException e) {
                claimsJws = null;
            }
        } else {
            claimsJws = null;
        }
        Jws<Claims> finalClaimsJws = claimsJws;
        String examToken = JwtTokenProvider.singletonInstance.generateToken(anonymous, jwtBuilder -> {
            Map<String, String> oAuth2Map;
            if (finalClaimsJws != null && finalClaimsJws.getHeader().get("OAuth2") instanceof Map<?, ?> map) {
                //noinspection unchecked
                oAuth2Map = (Map<String, String>) map;
            } else {
                oAuth2Map = new HashMap<>();
            }
            if (unbindOAuth2s != null) {
                for (String providerId : unbindOAuth2s) {
                    oAuth2Map.remove(providerId);
                }
            }
            jwtBuilder.header().add("OAuth2", oAuth2Map);

        });
        Cookie examTokenCookie = new Cookie("examToken", examToken);
        examTokenCookie.setPath("/checkIn");
        response.addCookie(examTokenCookie);
        return TOKEN_REFRESHED_SUCCESSFULLY_JSON;
    }

    @Transactional
    @RequestMapping(method = RequestMethod.POST, path = "/api/sign-up")
    public String signUpWith(@RequestBody SignUpRequest signUpRequest) throws JsonProcessingException, ExamException {
        final Optional<ExamData> optionalExamData = examDataService.findById(signUpRequest.examId);
        if (optionalExamData.isPresent()) {
            ExamData examData = optionalExamData.get();
            try {
                final String levelId = examData.getExamResult().getLevelId();
                GradingLevel level = gradingLevelService.findById(levelId);
                final GradingLevel.CreatingUserStrategy creatingUserStrategy = level.getCreatingUserStrategy();
                if (creatingUserStrategy == GradingLevel.CreatingUserStrategy.NOT_CREATE) {
                    return NOT_SUPPORTED_LEVEL_FOR_SIGN_UP_JSON;
                } else {
                    final boolean enabled = creatingUserStrategy == GradingLevel.CreatingUserStrategy.CREATE_AND_ENABLED;
                    userService.handleSignUp(examData, signUpRequest.name, signUpRequest.password, level.getCreatingUserRole(), enabled);
                    examData.setStatus(ExamData.Status.SIGN_UP_COMPLETED);
                    if (creatingUserStrategy == GradingLevel.CreatingUserStrategy.CREATE_AND_ENABLED ||
                            creatingUserStrategy == GradingLevel.CreatingUserStrategy.CREATE_AND_DISABLED) {
                        examData.getExamResult().setSignUpCompletingType(SignUpCompletingType.COMPLETED);
                    } else {
                        examData.getExamResult().setSignUpCompletingType(SignUpCompletingType.INSPECT_REQUIRED);
                    }
                    examData.sendUpdateExamRecord();
                    examDataService.save(examData);
                    Map<String, String> message = new HashMap<>();
                    message.put("type", "success");
                    message.put("message", "Signed up successfully");
                    message.put("completingType", creatingUserStrategy.name());
                    return objectMapper.writeValueAsString(message);
                }
            } catch (ExamIllegalStateException e) {
                return EXAM_NOT_SUBMITTED_JSON;
            } catch (ExamInvalidException e) {
                return EXAM_INVALIDED_JSON;
            } catch (ExamException e) {
                throw e;
            } catch (UserExistsException e) {
                return USER_EXISTS_JSON;
            } catch (Exception e) {
                Map<String, String> message = new HashMap<>();
                message.put("type", "error");
                message.put("message", e.getMessage());
                return objectMapper.writeValueAsString(message);
            }
        } else {
            return EXAM_IS_NOT_EXIST_JSON;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateRequest(long qq, List<String> partitionIds, String turnstileToken) {
    }

    public record GetQuestionsByIndexRequest(String examId, int[] indexes) {
    }

    public record GetResultRequest(String examId) {
    }

    public record SubmitRequest(String examId, Map<String, Object> answer) {
    }

    public record ProviderItem(String id, String name, String iconDomain, boolean required) {
        public static ProviderItem from(OAuth2ProviderInfo info) {
            return new ProviderItem(info.getId(), info.getName(), info.getIconDomain(), info.getExamLoginMode() == OAuth2ProviderInfo.ExamLoginMode.REQUIRED);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefreshTokenRequest(List<String> unbindOAuth2s) {
    }

    public record SignUpRequest(String examId, String name, String password) {
    }
}