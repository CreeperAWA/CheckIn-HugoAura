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
import indi.etern.checkIn.api.webSocket.Message;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

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
    private final BlacklistService blacklistService;
    private final AnswerLimitService answerLimitService;
    private final indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService thirdPartyApiWebSocketService;
    
    /**
     * 已验证的QQ号缓存（内存缓存，JVM重启后清空）
     * key: QQ号, value: 验证通过的时间戳
     * 验证成功后，该QQ号在配置的有效期内可免验证生成题目
     */
    private static final ConcurrentHashMap<String, Long> verifiedQQCache = new ConcurrentHashMap<>();
    
    /**
     * 验证状态缓存（内存缓存，JVM重启后清空）
     * key: QQ号, value: 验证状态信息
     * 用于存储验证过程中的状态，供前端查询
     */
    private static final ConcurrentHashMap<String, VerifyStatusInfo> verifyStatusCache = new ConcurrentHashMap<>();
    
    /**
     * 验证状态信息
     */
    private static class VerifyStatusInfo {
        String status;
        String guideMessage;
        String verifyContent;
        long updatedAt;
        
        VerifyStatusInfo(String status, String guideMessage, String verifyContent) {
            this.status = status;
            this.guideMessage = guideMessage;
            this.verifyContent = verifyContent;
            this.updatedAt = System.currentTimeMillis();
        }
    }

    public ExamController(PartitionService partitionService, ActionExecutor actionExecutor, ExamGenerator examGenerator,
                          ExamDataService examDataService, ObjectMapper objectMapper, QuestionStatisticService questionStatisticService,
                          SettingService settingService, UserService userService, GradingLevelService gradingLevelService,
                          TurnstileService turnstileService, OAuth2Service oAuth2Service, JwtTokenProvider jwtTokenProvider,
                          BlacklistService blacklistService, AnswerLimitService answerLimitService, indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService thirdPartyApiWebSocketService) {
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
        this.blacklistService = blacklistService;
        this.answerLimitService = answerLimitService;
        this.thirdPartyApiWebSocketService = thirdPartyApiWebSocketService;
    }

    @PostMapping(path = "/api/generate")
    @Transactional(noRollbackFor = Throwable.class)
    public String generateExam(@RequestBody GenerateRequest generateRequest, HttpServletRequest httpServletRequest) throws JsonProcessingException {
        logger.info("Received generate exam request for QQ: {}, partitionIds: {}", 
            generateRequest.qq, generateRequest.partitionIds);
        
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
            // 检查黑名单
            if (blacklistService.isBlacklisted(String.valueOf(generateRequest.qq))) {
                Map<String, String> errorDataMap = new HashMap<>();
                errorDataMap.put("type", "error");
                errorDataMap.put("description", "该 QQ 号已被禁止作答");
                return objectMapper.writeValueAsString(errorDataMap);
            }
            
            // 检查答题次数限制
            if (answerLimitService.hasExceededLimit(String.valueOf(generateRequest.qq))) {
                int maxCount = answerLimitService.getMaxAnswerCount();
                int currentCount = answerLimitService.getAnswerCount(String.valueOf(generateRequest.qq));
                Map<String, String> errorDataMap = new HashMap<>();
                errorDataMap.put("type", "error");
                errorDataMap.put("description", "答题次数已达上限，最多可答题 " + maxCount + " 次，您已答题 " + currentCount + " 次");
                return objectMapper.writeValueAsString(errorDataMap);
            }
            
            // 检查QQ验证是否已完成
            String qqStr = String.valueOf(generateRequest.qq);
            try {
                SettingItem qqVerifyEnabled = settingService.getItem("thirdPartyApi.qqVerify", "enabled");
                Boolean verifyEnabled = null;
                try {
                    verifyEnabled = qqVerifyEnabled.getValue(Boolean.class);
                } catch (Exception e) {
                    verifyEnabled = false;
                }
                
                if (Boolean.TRUE.equals(verifyEnabled)) {
                    // 检查是否在白名单
                    boolean inWhitelist = false;
                    try {
                        SettingItem whitelistSetting = settingService.getItem("thirdPartyApi.qqVerify", "whitelist");
                        List<?> whitelistRaw = whitelistSetting.getValue(List.class);
                        if (whitelistRaw != null && !whitelistRaw.isEmpty()) {
                            for (Object item : whitelistRaw) {
                                if (qqStr.equals(String.valueOf(item).trim())) {
                                    inWhitelist = true;
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to check whitelist in generateExam", e);
                    }
                    
                    // 检查是否在已验证缓存中
                    boolean inVerifiedCache = false;
                    try {
                        Long verifiedTime = verifiedQQCache.get(qqStr);
                        if (verifiedTime != null) {
                            int validDays = 1;
                            try {
                                SettingItem validDaysSetting = settingService.getItem("thirdPartyApi.qqVerify", "validDays");
                                validDays = validDaysSetting.getValue(Integer.class);
                                if (validDays <= 0) {
                                    validDays = 1;
                                }
                            } catch (Exception e) {
                                logger.warn("Failed to get validDays setting", e);
                            }
                            long validDurationMs = validDays * 24L * 60L * 60L * 1000L;
                            if ((System.currentTimeMillis() - verifiedTime) < validDurationMs) {
                                inVerifiedCache = true;
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to check verified cache in generateExam", e);
                    }
                    
                    // 如果不在白名单且不在验证缓存中，检查验证状态
                    if (!inWhitelist && !inVerifiedCache) {
                        VerifyStatusInfo statusInfo = verifyStatusCache.get(qqStr);
                        if (statusInfo == null || !"success".equals(statusInfo.status)) {
                            Map<String, String> errorDataMap = new HashMap<>();
                            errorDataMap.put("type", "error");
                            errorDataMap.put("description", "请先完成QQ号验证");
                            return objectMapper.writeValueAsString(errorDataMap);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to check QQ verification status in generateExam", e);
            }
            
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

    @RequestMapping(method = RequestMethod.POST, path = "/api/check-qq-verify")
    public Map<String, Object> checkQQVerify(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long qq = Long.valueOf(request.get("qq").toString());
            String qqStr = String.valueOf(qq);
            
            // 检查是否有第三方API客户端连接
            if (indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiConnector.CONNECTORS.isEmpty()) {
                response.put("needVerify", false);
                response.put("error", "无可用的验证客户端");
                return response;
            }
            
            // 检查验证功能是否启用
            SettingItem qqVerifyEnabled = settingService.getItem("thirdPartyApi.qqVerify", "enabled");
            Boolean verifyEnabled = null;
            try {
                verifyEnabled = qqVerifyEnabled.getValue(Boolean.class);
            } catch (Exception e) {
                logger.warn("Failed to get qqVerifyEnabled setting, defaulting to false", e);
                verifyEnabled = false;
            }
            
            if (!Boolean.TRUE.equals(verifyEnabled)) {
                // 验证功能未启用，不需要验证
                response.put("needVerify", false);
                return response;
            }
            
            // 检查是否在白名单中
            try {
                SettingItem whitelistSetting = settingService.getItem("thirdPartyApi.qqVerify", "whitelist");
                List<?> whitelistRaw = whitelistSetting.getValue(List.class);
                if (whitelistRaw != null && !whitelistRaw.isEmpty()) {
                    for (Object item : whitelistRaw) {
                        if (qqStr.equals(String.valueOf(item).trim())) {
                            // 在白名单中，不需要验证
                            response.put("needVerify", false);
                            return response;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to check whitelist", e);
            }
            
            // 检查是否已验证且在有效期内
            try {
                Long verifiedTime = verifiedQQCache.get(qqStr);
                if (verifiedTime != null) {
                    // 获取配置的有效期（天数）
                    int validDays = 1; // 默认1天
                    try {
                        SettingItem validDaysSetting = settingService.getItem("thirdPartyApi.qqVerify", "validDays");
                        validDays = validDaysSetting.getValue(Integer.class);
                        if (validDays <= 0) {
                            validDays = 1;
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to get validDays setting, using default 1 day", e);
                    }
                    
                    long validDurationMs = validDays * 24L * 60L * 60L * 1000L;
                    if ((System.currentTimeMillis() - verifiedTime) < validDurationMs) {
                        // 验证仍在有效期内，不需要验证
                        response.put("needVerify", false);
                        return response;
                    } else {
                        // 验证已过期，清除缓存
                        verifiedQQCache.remove(qqStr);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to check verified cache", e);
            }
            
            // 提前生成验证内容，在回调中使用
            String[] verifyInfo = new String[2];
            java.util.concurrent.atomic.AtomicReference<String> verifyContentRef = new java.util.concurrent.atomic.AtomicReference<>(generateRandomVerifyCode());
            java.util.concurrent.atomic.AtomicReference<String> guideMessageRef = new java.util.concurrent.atomic.AtomicReference<>("请按照以下步骤进行验证");
            
            // 获取自定义验证字符串列表
            try {
                SettingItem customStringsSetting = settingService.getItem("thirdPartyApi.qqVerify", "customStrings");
                String customStringsJson = customStringsSetting.getValue(String.class);
                if (customStringsJson != null && !customStringsJson.isBlank()) {
                    List<String> customStrings = objectMapper.readValue(customStringsJson, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    if (customStrings != null && !customStrings.isEmpty()) {
                        java.util.Random random = new java.util.Random();
                        verifyContentRef.set(customStrings.get(random.nextInt(customStrings.size())));
                    }
                }
            } catch (Exception e) {
                logger.debug("No custom strings configured, using random code", e);
            }
            
            // 获取引导消息
            try {
                SettingItem guideMessageSetting = settingService.getItem("thirdPartyApi.qqVerify", "guideMessage");
                guideMessageRef.set(guideMessageSetting.getValue(String.class));
            } catch (Exception e) {
                logger.debug("Failed to get guide message setting", e);
            }
            
            verifyInfo[0] = verifyContentRef.get();
            verifyInfo[1] = guideMessageRef.get();
            
            // 需要验证，向第三方 WebSocket 服务发送验证询问
            java.util.concurrent.atomic.AtomicReference<Boolean> needVerifyRef = new java.util.concurrent.atomic.AtomicReference<>(false);
            java.util.concurrent.CountDownLatch checkLatch = new java.util.concurrent.CountDownLatch(1);
            
            thirdPartyApiWebSocketService.sendQQVerifyCheck(qqStr, new indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService.VerifyCheckRequest.VerifyCheckCallback() {
                @Override
                public void onResponse(Boolean needVerify) {
                    needVerifyRef.set(needVerify);
                    if (needVerify) {
                        final String verifyContent = verifyContentRef.get();
                        // 发送验证请求给第三方（不等待完成，立即返回给前端）
                        thirdPartyApiWebSocketService.sendQQVerifyRequest(qqStr, verifyContent, new indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService.VerifyRequest.VerifyCallback() {
                            @Override
                            public void onResponse(String status, String message) {
                                // 验证成功时加入缓存
                                if ("success".equals(status)) {
                                    try {
                                        int validDays = 1;
                                        try {
                                            SettingItem validDaysSetting = settingService.getItem("thirdPartyApi.qqVerify", "validDays");
                                            validDays = validDaysSetting.getValue(Integer.class);
                                            if (validDays <= 0) {
                                                validDays = 1;
                                            }
                                        } catch (Exception e) {
                                            logger.warn("Failed to get validDays setting", e);
                                        }
                                        verifiedQQCache.put(qqStr, System.currentTimeMillis());
                                        logger.info("QQ {} verification succeeded, added to cache", qqStr);
                                    } catch (Exception e) {
                                        logger.error("Failed to add QQ to verified cache", e);
                                    }
                                }
                                
                                // 更新验证状态缓存
                                String resultGuideMessage = message;
                                if (resultGuideMessage == null || resultGuideMessage.isBlank()) {
                                    resultGuideMessage = getDefaultGuideMessageForStatus(status);
                                }
                                verifyStatusCache.put(qqStr, new VerifyStatusInfo(status, resultGuideMessage, verifyContent));
                            }
                        });
                    }
                    checkLatch.countDown();
                }
            });
            
            // 等待验证询问响应（35秒超时）
            if (!checkLatch.await(35, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.warn("QQ verify check timeout for QQ: {}", qqStr);
                response.put("needVerify", false);
                response.put("error", "验证检查超时");
                return response;
            }
            
            Boolean needVerify = needVerifyRef.get();
            if (needVerify != null && needVerify) {
                response.put("needVerify", true);
                response.put("guideMessage", verifyInfo[1]);
                response.put("verifyContent", verifyInfo[0]);
            } else {
                // 第三方API返回无需验证，添加验证状态缓存和已验证缓存
                verifyStatusCache.put(qqStr, new VerifyStatusInfo("success", "无需验证", ""));
                verifiedQQCache.put(qqStr, System.currentTimeMillis());
                response.put("needVerify", false);
            }
            
        } catch (Exception e) {
            logger.error("Check QQ verify failed", e);
            response.put("needVerify", false);
            response.put("error", "验证检查异常");
        }
        return response;
    }
    
    /**
     * 根据验证状态获取默认的引导消息
     */
    private String getDefaultGuideMessageForStatus(String status) {
        return switch (status) {
            case "failed" -> "验证失败，请重新验证";
            case "timeout" -> "验证操作超时，请重新验证";
            case "cannot_verify" -> "服务异常，请坐和放宽，稍后再试";
            default -> "验证状态未知，请重试";
        };
    }
    
    @RequestMapping(method = RequestMethod.POST, path = "/api/get-qq-verify-status")
    public Map<String, Object> getQQVerifyStatus(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long qq = Long.valueOf(request.get("qq").toString());
            String qqStr = String.valueOf(qq);
            
            // 检查验证状态缓存
            VerifyStatusInfo statusInfo = verifyStatusCache.get(qqStr);
            if (statusInfo != null) {
                response.put("status", statusInfo.status);
                response.put("guideMessage", statusInfo.guideMessage);
                response.put("verifyContent", statusInfo.verifyContent);
                
                // 如果是验证成功，清除缓存
                if ("success".equals(statusInfo.status)) {
                    verifyStatusCache.remove(qqStr);
                }
            } else {
                // 未找到验证状态，可能是用户没有按照引导执行操作
                response.put("status", "pending");
                response.put("guideMessage", "未找到验证记录，请按照引导操作");
            }
        } catch (Exception e) {
            logger.error("Get QQ verify status failed", e);
            response.put("status", "error");
            response.put("guideMessage", "获取验证状态异常");
        }
        return response;
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

    /**
     * 生成随机验证代码（12位，包含大小写英文字母）
     * @return 随机验证代码
     */
    private String generateRandomVerifyCode() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder(12);
        Random random = new Random();
        for (int i = 0; i < 12; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }
}