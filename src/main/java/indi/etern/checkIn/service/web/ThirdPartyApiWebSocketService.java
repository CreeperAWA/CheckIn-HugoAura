package indi.etern.checkIn.service.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.api.thirdPartyApiWebSocket.ThirdPartyApiConnector;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ThirdPartyApiWebSocketService {
    private static final Logger logger = LoggerFactory.getLogger(ThirdPartyApiWebSocketService.class);
    public static ThirdPartyApiWebSocketService singletonInstance;
    private final ObjectMapper objectMapper;
    private final BlacklistRepository blacklistRepository;

    // 验证请求的回调，用于处理验证响应
    @Getter
    /**
     * 验证询问请求
     */
    public static class VerifyCheckRequest {
        private final String messageId;
        private final String qq;
        private final long createdAt;
        private VerifyCheckCallback callback;

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

        public interface VerifyCheckCallback {
            void onResponse(Boolean needVerify);
            void onTimeout();
        }
    }

    // 存储等待验证询问响应的请求
    private final Map<String, VerifyCheckRequest> pendingVerifyCheckRequests = new ConcurrentHashMap<>();

    public static class VerifyRequest {
        private final String messageId;
        private final String qq;
        private final long createdAt;
        private VerifyCallback callback;

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

        public interface VerifyCallback {
            void onResponse(String status, String message);
            void onTimeout();
        }
    }

    // 存储等待验证响应的请求
    private final Map<String, VerifyRequest> pendingVerifyRequests = new ConcurrentHashMap<>();

    protected ThirdPartyApiWebSocketService(ObjectMapper objectMapper, BlacklistRepository blacklistRepository) {
        singletonInstance = this;
        this.objectMapper = objectMapper;
        this.blacklistRepository = blacklistRepository;
    }

    @SneakyThrows
    public void onThirdPartyApiAuthenticated(ThirdPartyApiConnector connector) {
        // 推送完整黑名单列表
        sendBlacklistFull(connector);
    }

    @SneakyThrows
    private void sendBlacklistFull(ThirdPartyApiConnector connector) {
        List<Blacklist> blacklists = blacklistRepository.findAll();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Blacklist blacklist : blacklists) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", blacklist.getId());
            item.put("qq", blacklist.getTargetId());
            item.put("reason", blacklist.getReason());
            item.put("created_at", toTimeArray(blacklist.getCreatedAt()));
            list.add(item);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        Message<Map<String, Object>> message = Message.of("blacklist_full", UUIDv7.randomUUID().toString(), data);
        connector.sendMessage(message);
    }

    public void sendBlacklistAdd(Blacklist blacklist) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", blacklist.getId());
        data.put("qq", blacklist.getTargetId());
        data.put("reason", blacklist.getReason());
        data.put("created_at", toTimeArray(blacklist.getCreatedAt()));
        Message<Map<String, Object>> message = Message.of("blacklist_add", UUIDv7.randomUUID().toString(), data);
        sendToAllThirdPartyApis(message);
    }

    public void sendBlacklistRemove(String id, String qq) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("qq", qq);
        Message<Map<String, Object>> message = Message.of("blacklist_remove", UUIDv7.randomUUID().toString(), data);
        sendToAllThirdPartyApis(message);
    }

    /**
     * 发送验证询问请求（第一步：询问是否需要验证）
     */
    @SneakyThrows
    public void sendQQVerifyCheck(String qq, VerifyCheckRequest.VerifyCheckCallback callback) {
        String messageId = UUIDv7.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("qq", qq);
        Message<Map<String, Object>> message = Message.of("qq_verify_check", messageId, data);
        
        VerifyCheckRequest request = new VerifyCheckRequest(messageId, qq, callback);
        pendingVerifyCheckRequests.put(messageId, request);
        
        sendToAllThirdPartyApis(message);
        
        // 设置30秒超时
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                VerifyCheckRequest req = pendingVerifyCheckRequests.remove(messageId);
                if (req != null) {
                    logger.warn("QQ verify check timeout for QQ: {}", qq);
                    req.getCallback().onTimeout();
                }
            }
        }, 30 * 1000);
    }
    
    /**
     * 处理验证询问响应
     */
    @SneakyThrows
    public void handleQQVerifyCheckResponse(ThirdPartyApiConnector connector, JsonRawMessage message) {
        Map<String, Object> data = objectMapper.readValue(message.getData(), Map.class);
        String messageId = message.getMessageId();
        String qq = (String) data.get("qq");
        Boolean needVerify = (Boolean) data.get("need_verify");
        
        logger.info("Received QQ verify check response for QQ: {}, need_verify: {}", qq, needVerify);
        
        VerifyCheckRequest request = pendingVerifyCheckRequests.remove(messageId);
        if (request != null && request.getQq().equals(qq)) {
            request.getCallback().onResponse(needVerify);
        } else {
            logger.warn("No pending verify check request found for messageId: {}", messageId);
        }
    }

    /**
     * 发送验证请求（第二步：执行验证）
     */
    @SneakyThrows
    public void sendQQVerifyRequest(String qq, String verifyContent, VerifyRequest.VerifyCallback callback) {
        String messageId = UUIDv7.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("qq", qq);
        data.put("verify_content", verifyContent);
        Message<Map<String, Object>> message = Message.of("qq_verify_request", messageId, data);
        
        VerifyRequest request = new VerifyRequest(messageId, qq, callback);
        pendingVerifyRequests.put(messageId, request);
        
        sendToAllThirdPartyApis(message);
        
        // 设置2分钟超时
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                VerifyRequest req = pendingVerifyRequests.remove(messageId);
                if (req != null) {
                    req.getCallback().onTimeout();
                }
            }
        }, 2 * 60 * 1000);
    }

    @SneakyThrows
    public void handleQQVerifyResponse(ThirdPartyApiConnector connector, JsonRawMessage message) {
        Map<String, Object> data = objectMapper.readValue(message.getData(), Map.class);
        String messageId = message.getMessageId();
        String qq = (String) data.get("qq");
        String status = (String) data.get("status");
        String responseMessage = (String) data.get("message");
        
        VerifyRequest request = pendingVerifyRequests.remove(messageId);
        if (request != null && request.getQq().equals(qq)) {
            request.getCallback().onResponse(status, responseMessage);
        }
    }
    
    @SneakyThrows
    public void handleQQVerifyResponseFromUser(indi.etern.checkIn.api.webSocket.Connector connector, JsonRawMessage message) {
        Map<String, Object> data = objectMapper.readValue(message.getData(), Map.class);
        String messageId = message.getMessageId();
        String qq = (String) data.get("qq");
        String status = (String) data.get("status");
        String responseMessage = (String) data.get("message");
        
        logger.info("Received QQ verify response from user connector for QQ: {}, status: {}", qq, status);
        
        VerifyRequest request = pendingVerifyRequests.get(messageId);
        if (request != null && request.getQq().equals(qq)) {
            pendingVerifyRequests.remove(messageId);
            request.getCallback().onResponse(status, responseMessage);
        } else {
            logger.warn("No pending verify request found for messageId: {} or QQ mismatch (expected: {}, got: {})", messageId, request != null ? request.getQq() : "null", qq);
        }
    }

    public void sendNotification(String type, Map<String, Object> data) {
        Message<Map<String, Object>> message = Message.of(type, UUIDv7.randomUUID().toString(), data);
        sendToAllThirdPartyApis(message);
    }
    
    public void sendLoginSuccessNotification(String username, long qq, String permissionGroup) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("qq", String.valueOf(qq));
        data.put("login_time", toTimeArray(LocalDateTime.now()));
        data.put("permission_group", permissionGroup);
        sendNotification("notification_login_success", data);
    }
    
    public void sendLoginFailureNotification(String username, String password) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("password", password);
        data.put("fail_time", toTimeArray(LocalDateTime.now()));
        sendNotification("notification_login_failure", data);
    }

    @SneakyThrows
    private void sendToAllThirdPartyApis(Message<?> message) {
        String messageStr = objectMapper.writeValueAsString(message);
        logger.debug("ThirdPartyApi webSocket to all, msg:{}", messageStr);
        for (ThirdPartyApiConnector connector : ThirdPartyApiConnector.CONNECTORS) {
            if (connector.isOpen()) {
                try {
                    connector.sendMessageWithoutLog(messageStr);
                } catch (IOException e) {
                    logger.error("Error sending message to ThirdPartyApi sid_{}", connector.getSid(), e);
                }
            }
        }
    }

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
