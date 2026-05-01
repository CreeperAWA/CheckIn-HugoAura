package indi.etern.checkIn.service.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiConnector;
import indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiMessageBuilder;
import indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiMessageTypes;
import indi.etern.checkIn.api.webSocket.JsonRawMessage;
import indi.etern.checkIn.api.webSocket.Message;
import indi.etern.checkIn.entities.blacklist.Blacklist;
import indi.etern.checkIn.entities.setting.SettingItem;
import indi.etern.checkIn.repositories.BlacklistRepository;
import indi.etern.checkIn.service.dao.SettingService;
import indi.etern.checkIn.utils.RandomUtils;
import indi.etern.checkIn.utils.UUIDv7;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第三方API WebSocket服务
 * 
 * 负责管理与第三方API的业务逻辑交互，包括：
 * 1. 黑名单管理：推送完整黑名单、新增/移除通知
 * 2. QQ验证流程：验证询问、验证请求、响应处理
 * 3. 通知推送：登录成功/失败等系统通知
 * 4. 回调管理：管理等待响应的请求和超时处理
 * 
 * QQ验证流程说明：
 * 第一步：发送验证询问（qq_verify_check）
 *   - 服务器询问第三方API是否需要验证该QQ号
 *   - 第三方API回复是否需要验证（qq_verify_check_response）
 * 第二步：发送验证请求（qq_verify_request）
 *   - 如果需要验证，服务器发送验证内容和验证请求
 *   - 第三方API执行验证并返回结果（qq_verify_response）
 */
@Service
public class ThirdPartyApiWebSocketService {
    
    private static final Logger logger = LoggerFactory.getLogger(ThirdPartyApiWebSocketService.class);
    
    /**
     * 单例实例引用，供非Spring管理的类调用
     */
    public static ThirdPartyApiWebSocketService singletonInstance;
    
    private final ObjectMapper objectMapper;
    private final BlacklistRepository blacklistRepository;
    private final SettingService settingService;
    @Lazy
    private final indi.etern.checkIn.service.dao.ExamDataService examDataService;
    
    /**
     * 等待验证询问响应的请求映射
     * key: 消息ID, value: 验证询问请求
     * 
     * 多用户隔离说明：
     * 每个验证请求使用唯一的messageId作为key，支持多个用户同时进行验证
     * 不同用户的验证请求不会相互干扰，因为每个请求都有独立的messageId和回调
     */
    @Getter
    private final Map<String, VerifyCheckRequest> pendingVerifyCheckRequests = new ConcurrentHashMap<>();
    
    /**
     * 等待验证响应的请求映射
     * key: 消息ID, value: 验证请求
     * 
     * 多用户隔离说明：
     * 每个验证请求使用唯一的messageId作为key，支持多个用户同时进行验证
     * 不同用户的验证请求不会相互干扰，因为每个请求都有独立的messageId和回调
     */
    @Getter
    private final Map<String, VerifyRequest> pendingVerifyRequests = new ConcurrentHashMap<>();
    
    protected ThirdPartyApiWebSocketService(ObjectMapper objectMapper, BlacklistRepository blacklistRepository, SettingService settingService, @Lazy indi.etern.checkIn.service.dao.ExamDataService examDataService) {
        singletonInstance = this;
        this.objectMapper = objectMapper;
        this.blacklistRepository = blacklistRepository;
        this.settingService = settingService;
        this.examDataService = examDataService;
    }
    
    /**
     * 验证询问请求
     * 
     * 用于记录发送给第三方API的验证询问，等待响应
     */
    public static class VerifyCheckRequest {
        private final String messageId;
        private final String qq;
        private final long createdAt;
        private VerifyCheckCallback callback;
        
        /**
         * 验证询问回调接口
         */
        public interface VerifyCheckCallback {
            /**
             * 收到响应时的回调
             * @param needVerify 是否需要验证
             */
            void onResponse(Boolean needVerify);
        }
        
        public VerifyCheckRequest(String messageId, String qq, VerifyCheckCallback callback) {
            this.messageId = messageId;
            this.qq = qq;
            this.createdAt = System.currentTimeMillis();
            this.callback = callback;
        }
        
        public String getMessageId() {
            return messageId;
        }
        
        public String getQq() {
            return qq;
        }
        
        public long getCreatedAt() {
            return createdAt;
        }
        
        public VerifyCheckCallback getCallback() {
            return callback;
        }
    }
    
    /**
     * 验证请求
     * 
     * 用于记录发送给第三方API的验证请求，等待响应
     */
    public static class VerifyRequest {
        private final String messageId;
        private final String qq;
        private final long createdAt;
        private VerifyCallback callback;
        
        /**
         * 验证回调接口
         */
        public interface VerifyCallback {
            /**
             * 收到响应时的回调
             * @param status 验证状态
             * @param message 响应消息
             */
            void onResponse(String status, String message);
        }
        
        public VerifyRequest(String messageId, String qq, VerifyCallback callback) {
            this.messageId = messageId;
            this.qq = qq;
            this.createdAt = System.currentTimeMillis();
            this.callback = callback;
        }
        
        public String getMessageId() {
            return messageId;
        }
        
        public String getQq() {
            return qq;
        }
        
        public long getCreatedAt() {
            return createdAt;
        }
        
        public VerifyCallback getCallback() {
            return callback;
        }
    }
    
    /**
     * 第三方API认证成功后的回调
     * 
     * 在第三方API通过Token认证后调用，推送初始数据
     * 
     * @param connector 已认证的连接器
     */
    @SneakyThrows
    public void onThirdPartyApiAuthenticated(ThirdPartyApiConnector connector) {
        sendBlacklistFull(connector);
    }
    
    /**
     * 发送完整黑名单列表
     * 
     * 在第三方API连接并认证成功后调用，推送当前完整黑名单
     * 
     * @param connector 目标连接器
     * @throws IOException 如果发送失败
     */
    @SneakyThrows
    private void sendBlacklistFull(ThirdPartyApiConnector connector) {
        List<Blacklist> blacklists = blacklistRepository.findAll();
        List<Map<String, Object>> list = new ArrayList<>(blacklists.size());
        
        for (Blacklist blacklist : blacklists) {
            list.add(buildBlacklistItemData(blacklist));
        }
        
        Message<Map<String, Object>> message = ThirdPartyApiMessageBuilder.createBlacklistFull(list);
        connector.sendMessage(message);
        logger.info("Sent full blacklist to ThirdPartyApi [{}], count: {}", connector.getSid(), list.size());
    }
    
    /**
     * 发送黑名单新增通知
     * 
     * 向所有已连接的第三方API广播新增的黑名单条目
     * 
     * @param blacklist 新增的黑名单记录
     */
    public void sendBlacklistAdd(Blacklist blacklist) {
        Map<String, Object> data = buildBlacklistItemData(blacklist);
        Message<Map<String, Object>> message = ThirdPartyApiMessageBuilder.createBlacklistAdd(
            blacklist.getId(), 
            blacklist.getTargetId(), 
            blacklist.getReason(), 
            toTimeArray(blacklist.getCreatedAt())
        );
        sendToAllThirdPartyApis(message);
        logger.info("Broadcast blacklist add notification, id: {}, qq: {}", blacklist.getId(), blacklist.getTargetId());
    }
    
    /**
     * 发送黑名单移除通知
     * 
     * 向所有已连接的第三方API广播移除的黑名单条目
     * 
     * @param id 黑名单记录ID
     * @param qq QQ号码
     */
    public void sendBlacklistRemove(String id, String qq) {
        Message<Map<String, Object>> message = ThirdPartyApiMessageBuilder.createBlacklistRemove(id, qq);
        sendToAllThirdPartyApis(message);
        logger.info("Broadcast blacklist remove notification, id: {}, qq: {}", id, qq);
    }
    
    /**
     * 构建黑名单条目数据
     */
    private Map<String, Object> buildBlacklistItemData(Blacklist blacklist) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", blacklist.getId());
        item.put("qq", blacklist.getTargetId());
        item.put("reason", blacklist.getReason());
        item.put("created_at", toTimeArray(blacklist.getCreatedAt()));
        return item;
    }
    
    /**
     * 发送QQ验证询问（第一步）
     * 
     * 向所有第三方API发送验证询问，询问是否需要验证指定QQ号
     * 
     * @param qq QQ号码
     * @param callback 验证询问回调
     */
    @SneakyThrows
    public void sendQQVerifyCheck(String qq, VerifyCheckRequest.VerifyCheckCallback callback) {
        String messageId = UUIDv7.randomUUID().toString();
        Message<Map<String, Object>> message = ThirdPartyApiMessageBuilder.createQQVerifyCheck(qq, messageId);
        
        VerifyCheckRequest request = new VerifyCheckRequest(messageId, qq, callback);
        pendingVerifyCheckRequests.put(messageId, request);
        
        sendToAllThirdPartyApis(message);
        logger.info("Sent QQ verify check for QQ: {}, messageId: {}", qq, messageId);
    }
    
    /**
     * 处理验证询问响应
     * 
     * 接收第三方API返回的验证询问响应，并触发对应回调
     * 优先通过MessageID匹配，如果失败则通过QQ号查找（兼容第三方返回不同MessageID的情况）
     * 
     * 多用户隔离机制：
     * - 每个用户的验证请求使用唯一的messageId
     * - 如果第三方正确回复messageId，直接匹配，完全隔离
     * - 如果第三方未正确回复，降级通过QQ号查找，确保正确匹配对应用户
     * 
     * @param connector 发送响应的连接器
     * @param message 响应消息
     * @throws IOException 如果解析消息失败
     */
    @SneakyThrows
    public void handleQQVerifyCheckResponse(ThirdPartyApiConnector connector, JsonRawMessage message) {
        Map<String, Object> data = objectMapper.readValue(message.getData(), Map.class);
        String messageId = message.getMessageId();
        String qq = (String) data.get("qq");
        Boolean needVerify = (Boolean) data.get("need_verify");
        
        logger.info("Received QQ verify check response from ThirdPartyApi [{}], QQ: {}, need_verify: {}, messageId: {}", 
            connector.getSid(), qq, needVerify, messageId);
        
        // 优先通过MessageID查找（正确实现时应当匹配）
        VerifyCheckRequest request = pendingVerifyCheckRequests.remove(messageId);
        
        // 如果没找到，尝试通过QQ号查找（兼容第三方API未正确使用messageId的情况）
        // 注意：在多用户场景下，不同用户的QQ号不同，因此通过QQ号查找也能正确隔离
        // 使用synchronized确保查找和删除的原子性，避免ConcurrentModificationException和并发竞争
        if (request == null) {
            synchronized (pendingVerifyCheckRequests) {
                String matchedKey = null;
                for (Map.Entry<String, VerifyCheckRequest> entry : pendingVerifyCheckRequests.entrySet()) {
                    if (entry.getValue().getQq().equals(qq)) {
                        matchedKey = entry.getKey();
                        break;
                    }
                }
                if (matchedKey != null) {
                    request = pendingVerifyCheckRequests.remove(matchedKey);
                    logger.warn("MessageID mismatch, fallback to QQ lookup. Original messageId: {}, response messageId: {}, QQ: {}", 
                        matchedKey, messageId, qq);
                }
            }
        }
        
        if (request != null) {
            logger.info("Matched verify check request for QQ: {}, need_verify: {}", qq, needVerify);
            request.getCallback().onResponse(needVerify);
        } else {
            logger.warn("No pending verify check request found for messageId: {} or QQ: {}. Response may be delayed or duplicate.", 
                messageId, qq);
        }
    }
    
    /**
     * 发送QQ验证请求（第二步）
     * 
     * 向所有第三方API发送验证请求，请求执行QQ验证
     * 
     * @param qq QQ号码
     * @param verifyContent 验证内容
     * @param callback 验证回调
     */
    @SneakyThrows
    public void sendQQVerifyRequest(String qq, String verifyContent, VerifyRequest.VerifyCallback callback) {
        String messageId = UUIDv7.randomUUID().toString();
        Message<Map<String, Object>> message = ThirdPartyApiMessageBuilder.createQQVerifyRequest(qq, verifyContent, messageId);
        
        VerifyRequest request = new VerifyRequest(messageId, qq, callback);
        pendingVerifyRequests.put(messageId, request);
        
        sendToAllThirdPartyApis(message);
        logger.info("Sent QQ verify request for QQ: {}, messageId: {}", qq, messageId);
    }
    
    /**
     * 处理QQ验证响应
     * 
     * 接收第三方API返回的验证响应，并触发对应回调
     * 
     * @param connector 发送响应的连接器
     * @param message 响应消息
     * @throws IOException 如果解析消息失败
     */
    @SneakyThrows
    public void handleQQVerifyResponse(ThirdPartyApiConnector connector, JsonRawMessage message) {
        Map<String, Object> data = objectMapper.readValue(message.getData(), Map.class);
        String messageId = message.getMessageId();
        String qq = (String) data.get("qq");
        String status = (String) data.get("status");
        String responseMessage = (String) data.get("message");
        
        logger.info("Received QQ verify response from ThirdPartyApi [{}], QQ: {}, status: {}", 
            connector.getSid(), qq, status);
        
        processVerifyResponse(messageId, qq, status, responseMessage);
    }
    
    /**
     * 处理来自用户连接器的QQ验证响应
     * 
     * 用于处理从用户WebSocket连接返回的验证响应
     * 
     * @param connector 用户连接器
     * @param message 响应消息
     * @throws IOException 如果解析消息失败
     */
    @SneakyThrows
    public void handleQQVerifyResponseFromUser(indi.etern.checkIn.api.webSocket.Connector connector, JsonRawMessage message) {
        Map<String, Object> data = objectMapper.readValue(message.getData(), Map.class);
        String messageId = message.getMessageId();
        String qq = (String) data.get("qq");
        String status = (String) data.get("status");
        String responseMessage = (String) data.get("message");
        
        logger.info("Received QQ verify response from user connector, QQ: {}, status: {}", qq, status);
        
        processVerifyResponse(messageId, qq, status, responseMessage);
    }
    
    /**
     * 处理验证响应的通用逻辑
     * 优先通过MessageID匹配，如果失败则通过QQ号查找
     * 
     * 使用synchronized确保原子操作，避免并发竞争条件：
     * - 防止多个线程同时通过QQ号查找到同一个请求
     * - 确保查找和删除操作的原子性
     */
    private void processVerifyResponse(String messageId, String qq, String status, String responseMessage) {
        // 优先通过MessageID查找
        VerifyRequest request = pendingVerifyRequests.remove(messageId);
        
        // 如果没找到，尝试通过QQ号查找（兼容第三方API返回不同MessageID的情况）
        // 使用synchronized确保查找和删除的原子性，避免并发竞争
        if (request == null) {
            synchronized (pendingVerifyRequests) {
                String matchedKey = null;
                for (Map.Entry<String, VerifyRequest> entry : pendingVerifyRequests.entrySet()) {
                    if (entry.getValue().getQq().equals(qq)) {
                        matchedKey = entry.getKey();
                        break;
                    }
                }
                if (matchedKey != null) {
                    request = pendingVerifyRequests.remove(matchedKey);
                    logger.warn("MessageID mismatch in verify response, fallback to QQ lookup. Original messageId: {}, response messageId: {}, QQ: {}, status: {}", 
                        matchedKey, messageId, qq, status);
                }
            }
        }
        
        if (request != null) {
            logger.info("Matched verify request for QQ: {}, status: {}", qq, status);
            request.getCallback().onResponse(status, responseMessage);
        } else {
            logger.warn("No pending verify request found for messageId: {} or QQ: {}. Response may be delayed or duplicate. Status: {}", 
                messageId, qq, status);
        }
    }
    
    /**
     * 处理考试记录查询请求
     * 
     * 接收第三方API发送的考试记录查询请求，返回该用户的所有历史记录
     * 
     * @param connector 发送请求的连接器
     * @param message 查询请求消息
     */
    @SneakyThrows
    public void handleExamRecordsQuery(ThirdPartyApiConnector connector, JsonRawMessage message) {
        Map<String, Object> data = objectMapper.readValue(message.getData(), Map.class);
        String messageId = message.getMessageId();
        String qq = (String) data.get("qq");
        
        logger.info("Received exam records query from ThirdPartyApi [{}], QQ: {}, messageId: {}", 
            connector.getSid(), qq, messageId);
        
        if (qq == null || qq.isBlank()) {
            sendMessageToConnector(connector, ThirdPartyApiMessageBuilder.createError(messageId, "QQ号不能为空"));
            return;
        }
        
        try {
            long qqNumber = Long.parseLong(qq);
            List<indi.etern.checkIn.entities.exam.ExamData> examDataList = examDataService.findAllByQQ(qqNumber);
            
            if (examDataList.isEmpty()) {
                sendMessageToConnector(connector, ThirdPartyApiMessageBuilder.createError(messageId, "未找到该用户的考试记录"));
                return;
            }
            
            examDataList.sort((a, b) -> {
                if (a.getGenerateTime() == null) return 1;
                if (b.getGenerateTime() == null) return -1;
                return b.getGenerateTime().compareTo(a.getGenerateTime());
            });
            
            List<Map<String, Object>> records = new ArrayList<>(examDataList.size());
            for (indi.etern.checkIn.entities.exam.ExamData examData : examDataList) {
                Map<String, Object> record = new HashMap<>();
                record.put("paper_id", examData.getId());
                record.put("status", examData.getStatus().name());
                record.put("generate_time", toTimeArray(examData.getGenerateTime()));
                
                if (examData.getExamResult() != null) {
                    record.put("score", examData.getExamResult().getScore());
                } else {
                    record.put("score", null);
                }
                
                if (examData.getSubmitTime() != null) {
                    record.put("submit_time", toTimeArray(examData.getSubmitTime()));
                } else {
                    record.put("submit_time", null);
                }
                
                records.add(record);
            }
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("qq", qq);
            responseData.put("records", records);
            
            Message<Map<String, Object>> responseMessage = ThirdPartyApiMessageBuilder.createExamRecordsResponse(
                messageId, responseData);
            sendMessageToConnector(connector, responseMessage);
            
            logger.info("Sent exam records response to ThirdPartyApi [{}], QQ: {}, count: {}", 
                connector.getSid(), qq, records.size());
        } catch (NumberFormatException e) {
            sendMessageToConnector(connector, ThirdPartyApiMessageBuilder.createError(messageId, "QQ号格式错误"));
        } catch (Exception e) {
            logger.error("Failed to query exam records for QQ: {}", qq, e);
            sendMessageToConnector(connector, ThirdPartyApiMessageBuilder.createError(messageId, "查询考试记录失败: " + e.getMessage()));
        }
    }
    
    /**
     * 处理考试无效化请求
     * 
     * 接收第三方API发送的考试无效化请求，将指定试卷标记为成绩无效
     * 
     * @param connector 发送请求的连接器
     * @param message 无效化请求消息
     */
    @SneakyThrows
    public void handleExamInvalidateRequest(ThirdPartyApiConnector connector, JsonRawMessage message) {
        Map<String, Object> data = objectMapper.readValue(message.getData(), Map.class);
        String messageId = message.getMessageId();
        String paperId = (String) data.get("paper_id");
        
        logger.info("Received exam invalidate request from ThirdPartyApi [{}], paper_id: {}, messageId: {}", 
            connector.getSid(), paperId, messageId);
        
        if (paperId == null || paperId.isBlank()) {
            sendMessageToConnector(connector, ThirdPartyApiMessageBuilder.createError(messageId, "试卷ID不能为空"));
            return;
        }
        
        try {
            Optional<indi.etern.checkIn.entities.exam.ExamData> optionalExamData = examDataService.findById(paperId);
            
            if (optionalExamData.isEmpty()) {
                sendMessageToConnector(connector, ThirdPartyApiMessageBuilder.createError(messageId, "试卷不存在"));
                return;
            }
            
            indi.etern.checkIn.entities.exam.ExamData examData = optionalExamData.get();
            
            if (examData.getStatus() != indi.etern.checkIn.entities.exam.ExamData.Status.SUBMITTED) {
                sendMessageToConnector(connector, ThirdPartyApiMessageBuilder.createExamInvalidateResponse(messageId, paperId, "failed"));
                return;
            }
            
            examData.setStatus(indi.etern.checkIn.entities.exam.ExamData.Status.SCORE_INVALIDED);
            examDataService.save(examData);
            examData.sendUpdateExamRecord();
            
            sendMessageToConnector(connector, ThirdPartyApiMessageBuilder.createExamInvalidateResponse(messageId, paperId, "success"));
            logger.info("Exam invalidated successfully, paper_id: {}", paperId);
        } catch (Exception e) {
            logger.error("Failed to invalidate exam, paper_id: {}", paperId, e);
            sendMessageToConnector(connector, ThirdPartyApiMessageBuilder.createError(messageId, "无效化考试失败: " + e.getMessage()));
        }
    }
    
    /**
     * 向指定连接器发送消息
     */
    @SneakyThrows
    private void sendMessageToConnector(ThirdPartyApiConnector connector, Message<?> message) {
        connector.sendMessage(message);
    }
    
    /**
     * 检查是否需要QQ验证（前端调用）
     * 
     * 流程：
     * 1. 检查是否启用QQ验证
     * 2. 检查用户是否在白名单或已验证缓存中
     * 3. 如果需要验证，向第三方发送 qq_verify_check 询问
     * 4. 返回结果给前端
     * 
     * @param userConnector 用户WebSocket连接器
     * @param userQQ 用户QQ号
     * @param requestMessage 请求消息
     */
    public void checkQQVerify(
        indi.etern.checkIn.api.webSocket.Connector userConnector, 
        String userQQ, 
        JsonRawMessage requestMessage
    ) {
        logger.info("Checking QQ verify for QQ: {}", userQQ);
        
        // 检查是否有第三方API客户端连接
        if (indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiConnector.CONNECTORS.isEmpty()) {
            sendCheckResultToUser(userConnector, false, null, null, "无可用的验证客户端");
            return;
        }
        
        // 检查是否启用验证
        try {
            SettingItem qqVerifyEnabled = settingService.getItem("thirdPartyApi.qqVerify", "enabled");
            Boolean verifyEnabled = null;
            try {
                verifyEnabled = qqVerifyEnabled.getValue(Boolean.class);
            } catch (Exception e) {
                logger.warn("Failed to get qqVerifyEnabled setting, defaulting to false", e);
                verifyEnabled = false;
            }
            
            if (!Boolean.TRUE.equals(verifyEnabled)) {
                sendCheckResultToUser(userConnector, false, null, null, null);
                return;
            }
            
            // 检查是否在白名单
            try {
                SettingItem whitelistSetting = settingService.getItem("thirdPartyApi.qqVerify", "whitelist");
                String whitelist = whitelistSetting.getValue(String.class);
                if (whitelist != null && !whitelist.isBlank()) {
                    String[] whiteQQs = whitelist.split("[,;，；\\s]+");
                    for (String whiteQQ : whiteQQs) {
                        if (whiteQQ.trim().equals(userQQ)) {
                            sendCheckResultToUser(userConnector, false, null, null, null);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to check whitelist", e);
            }
            
            // 检查是否已验证且在有效期内
            try {
                Class<?> examControllerClass = Class.forName("indi.etern.checkIn.controller.rest.ExamController");
                java.lang.reflect.Field cacheField = examControllerClass.getDeclaredField("verifiedQQCache");
                cacheField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.concurrent.ConcurrentHashMap<String, Long> cache = 
                    (java.util.concurrent.ConcurrentHashMap<String, Long>) cacheField.get(null);
                Long verifiedTime = cache.get(userQQ);
                if (verifiedTime != null && (System.currentTimeMillis() - verifiedTime) < 24 * 60 * 60 * 1000) {
                    sendCheckResultToUser(userConnector, false, null, null, null);
                    return;
                }
            } catch (Exception e) {
                logger.warn("Failed to check verified cache", e);
            }
            
            // 需要验证，向第三方询问
            sendQQVerifyCheck(userQQ, new VerifyCheckRequest.VerifyCheckCallback() {
                @Override
                public void onResponse(Boolean needVerify) {
                    logger.info("QQ verify check response for QQ {}: need_verify = {}", userQQ, needVerify);
                    
                    if (needVerify) {
                        // 需要验证，生成验证内容并发送给第三方
                        String verifyContent = RandomUtils.generateRandomVerifyCode();
                        String guideMessage = "请按照以下步骤进行验证：\n1. 按照提示完成验证\n2. 等待第三方系统确认\n3. 验证完成后系统将自动生成试题";
                        
                        // 先发送验证请求给第三方
                        sendQQVerifyRequest(userQQ, verifyContent, new VerifyRequest.VerifyCallback() {
                            @Override
                            public void onResponse(String status, String message) {
                                if ("success".equals(status)) {
                                    try {
                                        Class<?> examControllerClass = Class.forName("indi.etern.checkIn.controller.rest.ExamController");
                                        java.lang.reflect.Field cacheField = examControllerClass.getDeclaredField("verifiedQQCache");
                                        cacheField.setAccessible(true);
                                        @SuppressWarnings("unchecked")
                                        java.util.concurrent.ConcurrentHashMap<String, Long> cache = 
                                            (java.util.concurrent.ConcurrentHashMap<String, Long>) cacheField.get(null);
                                        cache.put(userQQ, System.currentTimeMillis());
                                        logger.info("QQ {} verification succeeded, added to cache", userQQ);
                                    } catch (Exception e) {
                                        logger.error("Failed to add QQ to verified cache", e);
                                    }
                                }
                                sendVerifyResultToUser(userConnector, status, message);
                            }
                        });
                        
                        // 然后通知前端显示验证窗口
                        try {
                            Map<String, Object> verifyData = new HashMap<>();
                            verifyData.put("type", "verify_required");
                            verifyData.put("verify_content", verifyContent);
                            verifyData.put("guide_message", guideMessage);
                            sendCheckResultToUserWithData(userConnector, true, verifyData, null);
                        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                            logger.error("Failed to serialize verify data", e);
                            sendCheckResultToUser(userConnector, false, null, null, "验证数据序列化失败");
                        }
                    } else {
                        sendCheckResultToUser(userConnector, false, null, null, null);
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("Error checking QQ verify", e);
            sendCheckResultToUser(userConnector, false, null, null, "验证检查异常");
        }
    }
    
    /**
     * 启动QQ验证（前端确认后调用，发送验证请求给第三方）
     * 
     * @param userConnector 用户WebSocket连接器
     * @param userQQ 用户QQ号
     * @param requestMessage 请求消息
     */
    public void startQQVerify(
        indi.etern.checkIn.api.webSocket.Connector userConnector, 
        String userQQ, 
        JsonRawMessage requestMessage
    ) {
        logger.info("Starting QQ verify for QQ: {}", userQQ);
        // 验证流程已经在 checkQQVerify 中启动，这里只需要确认即可
        // 实际验证结果会通过 qq_verify_result 消息推送给前端
        Map<String, Object> data = new HashMap<>();
        data.put("status", "verify_started");
        data.put("message", "验证已启动，请等待验证结果");
        
        try {
            sendToUserRaw(userConnector, "qq_verify_result", "verify_started", objectMapper.writeValueAsString(data));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to serialize verify started message", e);
        }
    }
    
    /**
     * 发送检查结果给用户
     */
    private void sendCheckResultToUser(
        indi.etern.checkIn.api.webSocket.Connector connector, 
        Boolean needVerify, 
        Map<String, Object> data, 
        String status,
        String message
    ) {
        Map<String, Object> result = new HashMap<>();
        result.put("need_verify", needVerify);
        if (data != null) {
            result.putAll(data);
        }
        if (status != null) {
            result.put("status", status);
        }
        if (message != null) {
            result.put("message", message);
        }
        
        try {
            sendToUserRaw(connector, "qq_verify_check_result", "success", objectMapper.writeValueAsString(result));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to serialize check result", e);
        }
    }
    
    /**
     * 发送检查结果给用户（带数据）
     */
    private void sendCheckResultToUserWithData(
        indi.etern.checkIn.api.webSocket.Connector connector, 
        Boolean needVerify, 
        Map<String, Object> data,
        String message
    ) throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> result = new HashMap<>();
        result.put("need_verify", needVerify);
        if (data != null) {
            result.putAll(data);
        }
        if (message != null) {
            result.put("message", message);
        }
        
        sendToUserRaw(connector, "qq_verify_check_result", "success", objectMapper.writeValueAsString(result));
    }
    
    /**
     * 启动验证流程（由前端通过WebSocket触发）
     * 
     * 完整流程：
     * 1. 发送 qq_verify_check 给第三方
     * 2. 第三方返回是否需要验证
     * 3. 如果需要验证：
     *    a. 发送 qq_verify_request 给第三方
     *    b. 推送验证数据给前端（显示验证窗口）
     *    c. 第三方返回验证结果
     *    d. 推送验证结果给前端
     * 4. 如果不需要验证，前端直接调用HTTP接口生成试题
     * 
     * @param userConnector 用户WebSocket连接器
     * @param userQQ 用户QQ号
     * @param requestMessage 请求消息
     */
    @Deprecated
    public void startVerificationFlow(
        indi.etern.checkIn.api.webSocket.Connector userConnector, 
        String userQQ, 
        JsonRawMessage requestMessage
    ) {
        logger.info("Starting verification flow for QQ: {}", userQQ);
        
        // 检查是否有第三方API客户端连接
        if (indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiConnector.CONNECTORS.isEmpty()) {
            sendVerifyResultToUser(userConnector, "error", "无可用的验证客户端");
            return;
        }
        
        // 发送验证询问给第三方
        sendQQVerifyCheck(userQQ, new VerifyCheckRequest.VerifyCheckCallback() {
            @Override
            public void onResponse(Boolean needVerify) {
                logger.info("QQ verify check response for QQ {}: need_verify = {}", userQQ, needVerify);
                
                try {
                    if (needVerify) {
                        // 需要验证，生成验证内容
                        String verifyContent = RandomUtils.generateRandomVerifyCode();
                        String guideMessage = "请按照以下步骤进行验证：\n1. 按照提示完成验证\n2. 验证完成后系统将自动生成试题";
                        
                        // 推送验证数据给前端，显示验证窗口
                        Map<String, Object> verifyData = new HashMap<>();
                        verifyData.put("type", "verify_required");
                        verifyData.put("verify_content", verifyContent);
                        verifyData.put("guide_message", guideMessage);
                        
                        try {
                            sendToUserRaw(userConnector, "qq_verify_result", "verify_check_result", objectMapper.writeValueAsString(verifyData));
                        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                            logger.error("Failed to serialize verify data", e);
                            sendVerifyResultToUser(userConnector, "error", "验证数据序列化失败");
                        }
                        
                        // 发送验证请求给第三方
                        sendQQVerifyRequest(userQQ, verifyContent, new VerifyRequest.VerifyCallback() {
                            @Override
                            public void onResponse(String status, String message) {
                                // 验证完成，处理结果
                                if ("success".equals(status)) {
                                    // 验证成功，加入缓存
                                    try {
                                        Class<?> examControllerClass = Class.forName("indi.etern.checkIn.controller.rest.ExamController");
                                        java.lang.reflect.Field cacheField = examControllerClass.getDeclaredField("verifiedQQCache");
                                        cacheField.setAccessible(true);
                                        @SuppressWarnings("unchecked")
                                        java.util.concurrent.ConcurrentHashMap<String, Long> cache = 
                                            (java.util.concurrent.ConcurrentHashMap<String, Long>) cacheField.get(null);
                                        cache.put(userQQ, System.currentTimeMillis());
                                        logger.info("QQ {} verification succeeded, added to cache", userQQ);
                                    } catch (Exception e) {
                                        logger.error("Failed to add QQ to verified cache", e);
                                    }
                                }
                                
                                // 推送验证结果给前端
                                sendVerifyResultToUser(userConnector, status, message);
                            }
                        });
                    } else {
                        // 不需要验证，通知前端可以直接生成试题
                        Map<String, Object> noVerifyData = new HashMap<>();
                        noVerifyData.put("type", "no_verify_needed");
                        try {
                            sendToUserRaw(userConnector, "qq_verify_result", "verify_check_result", objectMapper.writeValueAsString(noVerifyData));
                        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                            logger.error("Failed to serialize no-verify data", e);
                            sendVerifyResultToUser(userConnector, "error", "验证数据序列化失败");
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error processing verify check response", e);
                    sendVerifyResultToUser(userConnector, "error", "验证询问处理异常");
                }
            }
        });
    }
    
    /**
     * 发送验证结果给用户（简化版本，直接发送状态和消息）
     */
    private void sendVerifyResultToUser(indi.etern.checkIn.api.webSocket.Connector connector, String status, String message) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("status", status);
        resultData.put("message", message);
        
        try {
            sendToUserRaw(connector, "qq_verify_result", "verify_result", objectMapper.writeValueAsString(resultData));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to serialize verify result", e);
            // 如果序列化失败，尝试直接发送简单消息
            try {
                sendToUserRaw(connector, "qq_verify_result", "error", "{\"status\":\"error\",\"message\":\"验证结果序列化失败\"}");
            } catch (Exception ex) {
                logger.error("Failed to send fallback error message", ex);
            }
        }
    }
    
    /**
     * 向用户WebSocket连接发送已序列化的消息
     */
    private void sendToUserRaw(indi.etern.checkIn.api.webSocket.Connector connector, String type, String status, String messageJson) throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("message", messageJson);
        
        indi.etern.checkIn.api.webSocket.Message<Map<String, Object>> resultMessage = 
            indi.etern.checkIn.api.webSocket.Message.of(type, data);
        
        if (connector.isOpen()) {
            try {
                connector.sendMessage(resultMessage);
                logger.debug("Sent {} message to user connector, status: {}", type, status);
            } catch (java.io.IOException e) {
                logger.error("Failed to send message to user connector", e);
                throw new RuntimeException("发送消息失败", e);
            }
        } else {
            logger.warn("User connector is closed, cannot send message");
        }
    }
    
    /**
     * 向用户WebSocket连接发送消息
     */
    @Deprecated
    @SneakyThrows
    private void sendToUser(indi.etern.checkIn.api.webSocket.Connector connector, String type, String status, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("message", message);
        
        indi.etern.checkIn.api.webSocket.Message<Map<String, Object>> resultMessage = 
            indi.etern.checkIn.api.webSocket.Message.of(type, data);
        
        if (connector.isOpen()) {
            connector.sendMessage(resultMessage);
            logger.debug("Sent {} message to user connector, status: {}", type, status);
        } else {
            logger.warn("User connector is closed, cannot send message");
        }
    }
    
    /**
     * 发送通用通知消息
     * 
     * @param type 通知类型
     * @param data 通知数据
     */
    public void sendNotification(String type, Map<String, Object> data) {
        Message<Map<String, Object>> message = ThirdPartyApiMessageBuilder.createNotification(type, data);
        sendToAllThirdPartyApis(message);
        logger.info("Sent notification to all ThirdPartyApis, type: {}", type);
    }
    
    /**
     * 发送登录成功通知
     * 
     * @param username 用户名
     * @param qq QQ号码
     * @param permissionGroup 权限组
     */
    public void sendLoginSuccessNotification(String username, long qq, String permissionGroup) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("qq", String.valueOf(qq));
        data.put("login_time", toTimeArray(LocalDateTime.now()));
        data.put("permission_group", permissionGroup);
        sendNotification("notification_login_success", data);
    }
    
    /**
     * 发送登录失败通知
     * 
     * @param username 用户名
     * @param password 尝试的密码
     */
    public void sendLoginFailureNotification(String username, String password) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("password", password);
        data.put("fail_time", toTimeArray(LocalDateTime.now()));
        sendNotification("notification_login_failure", data);
    }
    
    /**
     * 向所有已连接的第三方API广播消息
     * 
     * 遍历所有活跃的连接器，发送消息给每个打开的连接
     * 如果发送失败，记录错误但不影响其他连接
     * 
     * @param message 要广播的消息
     * @throws IOException 如果序列化消息失败
     */
    @SneakyThrows
    private void sendToAllThirdPartyApis(Message<?> message) {
        String messageStr = objectMapper.writeValueAsString(message);
        logger.debug("ThirdPartyApi broadcast message, type: {}", message.getType().getName());
        
        for (ThirdPartyApiConnector connector : ThirdPartyApiConnector.CONNECTORS) {
            if (connector.isOpen()) {
                try {
                    connector.sendMessageWithoutLog(messageStr);
                } catch (java.lang.IllegalStateException e) {
                    logger.warn("WebSocket state conflict when sending to ThirdPartyApi [{}]: {}, message type: {}", 
                        connector.getSid(), e.getMessage(), message.getType().getName());
                } catch (IOException e) {
                    logger.error("Error sending message to ThirdPartyApi [{}]: {}", 
                        connector.getSid(), e.getMessage());
                }
            }
        }
    }
    
    /**
     * 将LocalDateTime转换为整型数组
     * 
     * 数组格式：[年, 月, 日, 时, 分, 秒, 纳秒]
     * 
     * @param dateTime 日期时间对象
     * @return 整型数组表示的时间，如果输入为null则返回null
     */
    private int[] toTimeArray(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return new int[]{
            dateTime.getYear(),
            dateTime.getMonthValue(),
            dateTime.getDayOfMonth(),
            dateTime.getHour(),
            dateTime.getMinute(),
            dateTime.getSecond(),
            dateTime.getNano()
        };
    }
}
