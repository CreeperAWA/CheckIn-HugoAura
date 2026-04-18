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
    
    public void sendLoginFailureNotification(int failCount, String username, String password) {
        Map<String, Object> data = new HashMap<>();
        data.put("fail_count", failCount);
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
