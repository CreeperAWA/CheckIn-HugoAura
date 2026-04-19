package indi.etern.checkIn.service.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiConnector;
import indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiMessageBuilder;
import indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiMessageTypes;
import indi.etern.checkIn.api.webSocket.JsonRawMessage;
import indi.etern.checkIn.api.webSocket.Message;
import indi.etern.checkIn.entities.blacklist.Blacklist;
import indi.etern.checkIn.repositories.BlacklistRepository;
import indi.etern.checkIn.utils.UUIDv7;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    
    /**
     * 超时时间常量
     */
    private static final long VERIFY_CHECK_TIMEOUT_MS = 30 * 1000; // 30秒
    private static final long VERIFY_REQUEST_TIMEOUT_MS = 2 * 60 * 1000; // 2分钟
    
    /**
     * 共享的调度线程池，避免频繁创建Timer导致资源泄漏
     * 核心线程数为2，足以处理所有超时任务
     */
    private static final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(
        2, 
        r -> {
            Thread t = new Thread(r, "VerifyTimeoutScheduler");
            t.setDaemon(true);
            return t;
        }
    );
    
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
    
    protected ThirdPartyApiWebSocketService(ObjectMapper objectMapper, BlacklistRepository blacklistRepository) {
        singletonInstance = this;
        this.objectMapper = objectMapper;
        this.blacklistRepository = blacklistRepository;
        
        // 注册JVM关闭钩子，优雅关闭超时调度线程池
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down timeout scheduler...");
            timeoutScheduler.shutdown();
            try {
                if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    timeoutScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                timeoutScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("Timeout scheduler shut down completed");
        }));
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
            
            /**
             * 超时时的回调
             */
            void onTimeout();
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
            
            /**
             * 超时时的回调
             */
            void onTimeout();
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
     * 设置30秒超时，超时后自动清理并调用onTimeout回调
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
        
        scheduleTimeout(messageId, VERIFY_CHECK_TIMEOUT_MS, 
            () -> {
                VerifyCheckRequest req = pendingVerifyCheckRequests.remove(messageId);
                if (req != null) {
                    logger.warn("QQ verify check timeout for QQ: {}, messageId: {}", qq, messageId);
                    req.getCallback().onTimeout();
                }
            },
            "QQ verify check timeout for QQ: " + qq
        );
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
     * 设置2分钟超时，超时后自动清理并调用onTimeout回调
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
        
        scheduleTimeout(messageId, VERIFY_REQUEST_TIMEOUT_MS,
            () -> {
                VerifyRequest req = pendingVerifyRequests.remove(messageId);
                if (req != null) {
                    logger.warn("QQ verify request timeout for QQ: {}, messageId: {}", qq, messageId);
                    req.getCallback().onTimeout();
                }
            },
            "QQ verify request timeout for QQ: " + qq
        );
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
     * 调度超时任务
     * 
     * 使用共享线程池，避免频繁创建Timer导致资源泄漏
     * 每个超时任务都是独立的，支持多用户并发验证互不干扰
     * 
     * @param messageId 消息ID（用于日志）
     * @param timeoutMs 超时时间（毫秒）
     * @param timeoutTask 超时回调任务
     * @param logMessage 超时日志消息
     */
    private void scheduleTimeout(String messageId, long timeoutMs, Runnable timeoutTask, String logMessage) {
        timeoutScheduler.schedule(
            () -> {
                try {
                    timeoutTask.run();
                } catch (Exception e) {
                    logger.error("Error executing timeout task for messageId {}: {}", messageId, e.getMessage(), e);
                }
            },
            timeoutMs,
            TimeUnit.MILLISECONDS
        );
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
