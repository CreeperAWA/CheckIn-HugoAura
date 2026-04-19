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
            
            // QQ号验证 - 两步流程
            try {
                SettingItem qqVerifyEnabled = settingService.getItem("thirdPartyApi.qqVerify", "enabled");
                Boolean verifyEnabled = null;
                try {
                    verifyEnabled = qqVerifyEnabled.getValue(Boolean.class);
                } catch (Exception e) {
                    logger.warn("Failed to get qqVerifyEnabled setting, defaulting to false", e);
                    verifyEnabled = false;
                }
                
                logger.info("QQ verification enabled status: {}", verifyEnabled);
                
                if (Boolean.TRUE.equals(verifyEnabled)) {
                    // 检查是否在白名单中
                    List<String> whitelist = Collections.emptyList();
                    try {
                        SettingItem whitelistSetting = settingService.getItem("thirdPartyApi.qqVerify", "whitelist");
                        List<?> whitelistRaw = whitelistSetting.getValue(List.class);
                        if (whitelistRaw != null) {
                            whitelist = whitelistRaw.stream()
                                    .map(obj -> obj != null ? obj.toString() : "")
                                    .filter(str -> !str.isEmpty())
                                    .collect(java.util.stream.Collectors.toList());
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to get whitelist setting", e);
                    }
                    
                    String qqStr = String.valueOf(generateRequest.qq);
                    logger.info("Checking QQ {} against whitelist: {}", qqStr, whitelist);
                    
                    if (!whitelist.contains(qqStr)) {
                        logger.info("QQ {} not in whitelist, proceeding with verification check", qqStr);
                        
                        // 检查是否有第三方API客户端连接
                        boolean hasThirdPartyConnected = !indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiConnector.CONNECTORS.isEmpty();
                        
                        logger.info("QQ verification - hasThirdPartyConnected: {}, thirdParty connectors count: {}", 
                                hasThirdPartyConnected, 
                                indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiConnector.CONNECTORS.size());
                        
                        if (!hasThirdPartyConnected) {
                            // 没有客户端连接，返回错误
                            Map<String, Object> errorDataMap = new HashMap<>();
                            errorDataMap.put("type", "error");
                            errorDataMap.put("description", "无可用的验证客户端");
                            errorDataMap.put("exceptionType", "NoVerifyClient");
                            return objectMapper.writeValueAsString(errorDataMap);
                        }
                        
                        // 第一步：发送验证询问
                        final CountDownLatch checkLatch = new CountDownLatch(1);
                        final AtomicReference<Boolean> needVerifyRef = new AtomicReference<>(false);
                        
                        thirdPartyApiWebSocketService.sendQQVerifyCheck(qqStr, new indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService.VerifyCheckRequest.VerifyCheckCallback() {
                            @Override
                            public void onResponse(Boolean needVerify) {
                                needVerifyRef.set(needVerify);
                                checkLatch.countDown();
                            }
                            
                            @Override
                            public void onTimeout() {
                                logger.warn("QQ verify check timeout for QQ: {}", qqStr);
                                needVerifyRef.set(true);
                                checkLatch.countDown();
                            }
                        });
                        
                        // 等待验证询问响应，最多30秒
                        boolean checkWaited = checkLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                        if (!checkWaited) {
                            logger.warn("QQ verify check latch timeout, assuming need verify for QQ: {}", qqStr);
                            needVerifyRef.set(true);
                        }
                        
                        boolean needVerify = needVerifyRef.get();
                        logger.info("QQ verify check result for QQ {}: need_verify = {}", qqStr, needVerify);
                        
                        if (!needVerify) {
                            // 不需要验证，继续生成试题
                            logger.info("QQ {} does not need verification, proceeding to generate exam", qqStr);
                        } else {
                            // 需要验证，生成验证内容
                            String verifyContent;
                            try {
                                SettingItem verifyContentSetting = settingService.getItem("thirdPartyApi.qqVerify", "customStrings");
                                List<String> customVerifyList = new java.util.ArrayList<>();
                                
                                if (verifyContentSetting != null) {
                                    Object rawValue = verifyContentSetting.getValue(Object.class);
                                    if (rawValue instanceof List) {
                                        List<?> list = (List<?>) rawValue;
                                        for (Object obj : list) {
                                            if (obj != null) {
                                                String str = obj.toString();
                                                if (!str.isEmpty()) {
                                                    customVerifyList.add(str);
                                                }
                                            }
                                        }
                                    } else if (rawValue instanceof String) {
                                        String jsonStr = (String) rawValue;
                                        if (!jsonStr.isEmpty() && !jsonStr.equals("[]")) {
                                            List<?> parsed = objectMapper.readValue(jsonStr, List.class);
                                            if (parsed != null) {
                                                for (Object obj : parsed) {
                                                    if (obj != null) {
                                                        String str = obj.toString();
                                                        if (!str.isEmpty()) {
                                                            customVerifyList.add(str);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                if (!customVerifyList.isEmpty()) {
                                    verifyContent = customVerifyList.get(new Random().nextInt(customVerifyList.size()));
                                } else {
                                    verifyContent = generateRandomVerifyCode();
                                }
                            } catch (Exception e) {
                                logger.warn("Failed to get custom strings setting, using random code", e);
                                verifyContent = generateRandomVerifyCode();
                            }
                            
                            // 获取引导提示信息
                            String guideMessage = "请按照以下步骤进行验证：\n1. 打开验证页面\n2. 输入验证内容\n3. 点击验证按钮";
                            try {
                                SettingItem guideMessageSetting = settingService.getItem("thirdPartyApi.qqVerify", "guideMessage");
                                if (guideMessageSetting != null) {
                                    String guideMsgValue = guideMessageSetting.getValue(String.class);
                                    if (guideMsgValue != null && !guideMsgValue.isEmpty()) {
                                        guideMessage = guideMsgValue;
                                    }
                                }
                            } catch (Exception e) {
                                logger.warn("Failed to get guide message setting, using default", e);
                            }
                            
                            // 第二步：发送验证请求（异步，不等待结果）
                            thirdPartyApiWebSocketService.sendQQVerifyRequest(qqStr, verifyContent, new indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService.VerifyRequest.VerifyCallback() {
                                @Override
                                public void onResponse(String status, String message) {
                                    // 验证完成，通知前端
                                    sendVerifyResultToFrontend(qqStr, status, message);
                                }
                                
                                @Override
                                public void onTimeout() {
                                    // 验证超时，通知前端
                                    sendVerifyResultToFrontend(qqStr, "timeout", "验证操作超时，请重新验证");
                                }
                            });
                            
                            // 立即返回验证数据给前端，让前端显示验证对话框
                            Map<String, Object> verifyDataMap = new HashMap<>();
                            verifyDataMap.put("type", "verify_required");
                            verifyDataMap.put("verify_content", verifyContent);
                            verifyDataMap.put("guide_message", guideMessage);
                            return objectMapper.writeValueAsString(verifyDataMap);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("QQ verification failed, blocking exam generation", e);
                Map<String, Object> errorDataMap = new HashMap<>();
                errorDataMap.put("type", "error");
                errorDataMap.put("description", "QQ号验证系统异常：" + e.getMessage());
                errorDataMap.put("exceptionType", "QQVerifyError");
                try {
                    return objectMapper.writeValueAsString(errorDataMap);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
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
            
            // 检查验证功能是否启用
            SettingItem qqVerifyEnabled = settingService.getItem("thirdPartyApi.qqVerify", "enabled");
            if (qqVerifyEnabled.getValue(Boolean.class)) {
                // 检查是否在白名单中
                SettingItem whitelistSetting = settingService.getItem("thirdPartyApi.qqVerify", "whitelist");
                List<String> whitelist = (List<String>) whitelistSetting.getValue(List.class);
                if (whitelist == null) {
                    whitelist = Collections.emptyList();
                }
                if (!whitelist.contains(qqStr)) {
                    // 需要验证，返回引导提示信息
                    String guideMessage = "请按照以下步骤进行验证：\n1. 打开验证页面\n2. 输入验证内容\n3. 点击验证按钮";
                    try {
                        SettingItem guideMessageSetting = settingService.getItem("thirdPartyApi.qqVerify", "guideMessage");
                        guideMessage = guideMessageSetting.getValue(String.class);
                    } catch (Exception e) {
                        logger.warn("Failed to get guide message setting", e);
                    }
                    response.put("needVerify", true);
                    response.put("guideMessage", guideMessage);
                    return response;
                }
            }
        } catch (Exception e) {
            logger.error("Check QQ verify failed", e);
        }
        // 不需要验证
        response.put("needVerify", false);
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
    
    /**
     * 向用户发送验证结果通知
     * 
     * 通过WebSocket向指定QQ号的用户发送验证结果
     * 
     * @param qq QQ号码
     * @param status 验证状态 (success, failed, timeout, cannot_verify)
     * @param message 验证消息
     */
    private void sendVerifyResultToFrontend(String qq, String status, String message) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("qq", qq);
            data.put("status", status);
            data.put("message", message);
            
            Message<Map<String, Object>> resultMessage = Message.of("qq_verify_result", data);
            
            // 查找对应的用户WebSocket连接并发送
            for (indi.etern.checkIn.api.webSocket.Connector connector : indi.etern.checkIn.api.webSocket.Connector.CONNECTORS) {
                if (connector.isOpen() && connector.getSid().equals(qq)) {
                    connector.sendMessage(resultMessage);
                    logger.info("Sent verify result to frontend, QQ: {}, status: {}", qq, status);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send verify result to frontend, QQ: {}", qq, e);
        }
    }
}