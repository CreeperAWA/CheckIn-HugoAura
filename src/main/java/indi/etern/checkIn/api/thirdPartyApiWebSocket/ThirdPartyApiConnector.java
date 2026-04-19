package indi.etern.checkIn.api.thirdPartyApiWebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.api.webSocket.JsonRawMessage;
import indi.etern.checkIn.api.webSocket.Message;
import indi.etern.checkIn.api.webSocket.interfaces.IMessage;
import indi.etern.checkIn.api.webSocket.PartRawMessageProcessor;
import indi.etern.checkIn.auth.JwtTokenProvider;
import indi.etern.checkIn.entities.user.User;
import indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService;
import indi.etern.checkIn.service.dao.SettingService;
import indi.etern.checkIn.service.dao.ThirdPartyApiTokenService;
import indi.etern.checkIn.entities.setting.SettingItem;
import io.jsonwebtoken.Claims;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 第三方API WebSocket连接器
 * 
 * 负责管理与第三方API客户端的WebSocket连接
 * 主要职责：
 * 1. 处理WebSocket生命周期事件（连接、关闭、错误）
 * 2. Token认证和验证
 * 3. 消息路由和分发
 * 4. 消息日志记录
 * 
 * 端点路径：/api/websocket/thirdParty/{sid}
 * 其中sid是第三方API的唯一标识符
 */
@Component
@ServerEndpoint("/api/websocket/thirdParty/{sid}")
@ConditionalOnWebApplication
public class ThirdPartyApiConnector {
    
    /**
     * 所有活跃的连接器集合，用于广播消息
     */
    public static final CopyOnWriteArraySet<ThirdPartyApiConnector> CONNECTORS = new CopyOnWriteArraySet<>();
    
    private static final Logger logger = LoggerFactory.getLogger(ThirdPartyApiConnector.class);
    
    private static ThirdPartyApiWebSocketService thirdPartyApiWebSocketService;
    private static JwtTokenProvider jwtTokenProvider;
    private static ObjectMapper objectMapper;
    private static SettingService settingService;
    private static ThirdPartyApiTokenService thirdPartyApiTokenService;
    
    /**
     * 消息缓冲区大小：64KB
     */
    private static final int BUFFER_SIZE = 64 * 1024;
    
    /**
     * 日志截断长度：512字符
     */
    private static final int LOG_TRUNCATE_SIZE = 512;
    
    private Session session;
    
    /**
     * 第三方API的唯一标识符（从URL路径参数获取）
     */
    @Getter
    private String sid = "";
    
    /**
     * 是否已通过Token认证
     */
    private boolean authenticated = false;
    
    /**
     * 分片消息处理器集合，用于重组分片消息
     * key: 消息ID, value: 分片消息处理器
     */
    private final Map<String, PartRawMessageProcessor> partMessageMap = new HashMap<>();
    
    @Autowired
    public void setThirdPartyApiWebSocketService(ThirdPartyApiWebSocketService thirdPartyApiWebSocketService) {
        ThirdPartyApiConnector.thirdPartyApiWebSocketService = thirdPartyApiWebSocketService;
    }

    @Autowired
    public void setJwtTokenProvider(JwtTokenProvider jwtTokenProvider) {
        ThirdPartyApiConnector.jwtTokenProvider = jwtTokenProvider;
    }

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        ThirdPartyApiConnector.objectMapper = objectMapper;
    }

    @Autowired
    public void setSettingService(SettingService settingService) {
        ThirdPartyApiConnector.settingService = settingService;
    }

    @Autowired
    public void setThirdPartyApiTokenService(ThirdPartyApiTokenService thirdPartyApiTokenService) {
        ThirdPartyApiConnector.thirdPartyApiTokenService = thirdPartyApiTokenService;
    }

    /**
     * 连接打开时的处理
     * 
     * @param session WebSocket会话
     * @param sid 第三方API标识符（从URL路径参数获取）
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        this.session = session;
        session.setMaxTextMessageBufferSize(BUFFER_SIZE);
        session.setMaxBinaryMessageBufferSize(0);
        CONNECTORS.add(this);
        this.sid = sid;
        logger.info("ThirdPartyApi [{}]: connected", sid);
    }

    /**
     * 连接错误处理
     * 
     * 区分IO异常（可能是正常断开）和其他异常（需要记录完整堆栈）
     * 
     * @param throwable 异常对象
     */
    @OnError
    public void onError(Throwable throwable) {
        String logPrefix = buildLogPrefix();
        if (throwable instanceof IOException) {
            logger.trace("{}: connection error (possibly normal disconnection)", logPrefix, throwable);
        } else {
            logger.error("{}: unexpected error", logPrefix, throwable);
        }
    }

    /**
     * 连接关闭时的处理
     * 
     * 从连接器集合中移除自身，释放资源
     */
    @SneakyThrows
    @OnClose
    public void onClose() {
        CONNECTORS.remove(this);
        logger.info("ThirdPartyApi [{}]: closed", sid);
    }

    /**
     * 接收消息时的处理
     * 
     * 消息处理流程：
     * 1. 解析JSON消息
     * 2. 验证Token认证状态
     * 3. 根据消息类型分发处理
     * 
     * 支持的消息类型：
     * - ping: 心跳请求，回复pong
     * - partMessage: 分片消息，用于大消息传输
     * - qq_verify_check_response: QQ验证询问响应
     * - qq_verify_response: QQ验证结果响应
     * 
     * @param message 原始JSON消息字符串
     */
    @OnMessage
    public void onMessage(String message) {
        String logPrefix = buildLogPrefix();
        try {
            JsonRawMessage contextJsonMessage = objectMapper.readValue(message, JsonRawMessage.class);
            
            if (!checkToken(contextJsonMessage)) {
                return;
            }
            
            if (!authenticated) {
                return;
            }
            
            String contextId = contextJsonMessage.getMessageId();
            String typeName = contextJsonMessage.getType().getName();
            
            switch (typeName) {
                case ThirdPartyApiMessageTypes.PING -> 
                    this.sendMessageWithoutLog(ThirdPartyApiMessageBuilder.createPong(contextId));
                    
                case ThirdPartyApiMessageTypes.PART_MESSAGE -> 
                    handlePartMessage(contextJsonMessage, contextId);
                    
                case ThirdPartyApiMessageTypes.QQ_VERIFY_CHECK_RESPONSE -> 
                    thirdPartyApiWebSocketService.handleQQVerifyCheckResponse(this, contextJsonMessage);
                    
                case ThirdPartyApiMessageTypes.QQ_VERIFY_RESPONSE -> 
                    thirdPartyApiWebSocketService.handleQQVerifyResponse(this, contextJsonMessage);
                    
                default -> logUnknownMessage(message, logPrefix);
            }
        } catch (Exception e) {
            handleProcessingError(message, logPrefix, e);
        }
    }
    
    /**
     * 处理分片消息
     * 
     * @param contextJsonMessage 上下文JSON消息
     * @param contextId 消息ID
     */
    private void handlePartMessage(JsonRawMessage contextJsonMessage, String contextId) {
        try {
            PartMessageData partMessageData = objectMapper.readValue(
                contextJsonMessage.getData(), PartMessageData.class
            );
            
            PartRawMessageProcessor partRawMessageProcessor;
            String partId;
            
            if (partMessageData.messageIds != null) {
                partRawMessageProcessor = new PartRawMessageProcessor(partMessageData.messageIds);
                partMessageMap.put(contextId, partRawMessageProcessor);
                partId = contextId;
                sendMessage(ThirdPartyApiMessageBuilder.createSuccess(contextId));
            } else if (partMessageData.partId != null && partMessageData.messagePart != null) {
                partId = partMessageData.partId;
                partRawMessageProcessor = partMessageMap.get(partId);
                if (partRawMessageProcessor == null) {
                    sendError(contextId, "Invalid partId: no active part message found");
                    return;
                }
                partRawMessageProcessor.put(contextId, partMessageData.messagePart);
            } else {
                sendError(contextId, "Unsupported message format: missing messageIds or partId/messagePart");
                return;
            }
            
            if (partRawMessageProcessor.isComplete()) {
                onMessage(partRawMessageProcessor.toString());
                partMessageMap.remove(partId);
            }
        } catch (Exception e) {
            logger.error("Failed to handle part message: {}", e.getMessage(), e);
            try {
                sendError(contextId, "Failed to process part message: " + e.getMessage());
            } catch (IOException ex) {
                logger.error("Failed to send error message for part message: {}", ex.getMessage());
            }
        }
    }
    
    /**
     * 记录未知类型的消息
     */
    private void logUnknownMessage(String message, String logPrefix) {
        String logMessage = truncateMessage(message);
        logger.debug("{}: received message of unknown type: {}", logPrefix, logMessage);
    }
    
    /**
     * 处理消息处理过程中的错误
     */
    private void handleProcessingError(String message, String logPrefix, Exception e) {
        try {
            logger.error("{}: processing error - {}: {}", logPrefix, e.getClass().getName(), e.getMessage());
            
            String messageId = extractMessageId(message);
            if (messageId != null) {
                sendError(messageId, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } catch (Exception ex) {
            logger.error("{}: failed to send error message (original: \"{}\"): {}", 
                logPrefix, message, ex.getMessage());
        }
    }
    
    /**
     * 从原始消息中提取messageId
     */
    private String extractMessageId(String message) {
        try {
            Map<String, Object> messageMap = objectMapper.readValue(message, HashMap.class);
            Object messageIdObj = messageMap.get("messageId");
            return messageIdObj != null ? messageIdObj.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 截断消息用于日志记录
     */
    private String truncateMessage(String message) {
        if (message.length() > LOG_TRUNCATE_SIZE) {
            return message.substring(0, LOG_TRUNCATE_SIZE) + "\n=========(truncated)=========";
        }
        return message;
    }
    
    /**
     * 构建日志前缀
     */
    private String buildLogPrefix() {
        return "ThirdPartyApi [" + sid + "]";
    }

    /**
     * 发送错误响应消息
     * 
     * @param contextId 消息ID，用于关联请求和响应
     * @param data 错误描述信息
     * @throws IOException 如果发送失败
     */
    private void sendError(String contextId, String data) throws IOException {
        var message = ThirdPartyApiMessageBuilder.createError(contextId, data);
        sendMessage(message);
    }

    /**
     * Token认证消息数据结构
     */
    private record TokenMessage(String token) {}

    /**
     * 分片消息数据结构
     */
    private record PartMessageData(List<String> messageIds, String partId, String messagePart) {}

    /**
     * Token检查和认证处理
     * 
     * 认证流程：
     * 1. 如果消息类型是token，提取并验证token
     * 2. 验证token的subject是否为"thirdPartyApi"
     * 3. 验证token的issuer是否与URL路径中的sid匹配
     * 4. 验证token的有效性和注册状态
     * 5. 认证成功后调用onThirdPartyApiAuthenticated回调
     * 
     * @param message 待验证的消息
     * @return 是否允许继续处理该消息
     * @throws IOException 如果解析消息失败
     */
    private boolean checkToken(JsonRawMessage message) throws IOException {
        if (message.getType().equals(Message.Type.of(ThirdPartyApiMessageTypes.TOKEN))) {
            return authenticateWithToken(message);
        } else if (authenticated) {
            return true;
        } else {
            sendMessage(ThirdPartyApiMessageBuilder.createNotAuthenticatedError(message.getMessageId()));
            return false;
        }
    }
    
    /**
     * 使用Token进行认证
     * 
     * @param message 包含token的消息
     * @return 认证是否成功
     */
    private boolean authenticateWithToken(JsonRawMessage message) throws IOException {
        TokenMessage tokenMessage = objectMapper.readValue(message.getData(), TokenMessage.class);
        
        try {
            Claims claims = jwtTokenProvider.parseToken(tokenMessage.token).getPayload();
            String subject = claims.getSubject();
            String issuer = claims.getIssuer();
            
            if (!"thirdPartyApi".equals(subject)) {
                sendAuthenticationError(message.getMessageId(), "invalid token type");
                return false;
            }
            
            if (!sid.equals(issuer)) {
                sendAuthenticationError(message.getMessageId(), 
                    "token SID mismatch (expected: " + sid + ", got: " + issuer + ")");
                return false;
            }
            
            if (!jwtTokenProvider.validateToken(tokenMessage.token)) {
                sendAuthenticationError(message.getMessageId(), "invalid or expired token");
                return false;
            }
            
            if (!thirdPartyApiTokenService.existByToken(tokenMessage.token)) {
                sendAuthenticationError(message.getMessageId(), "token not registered");
                return false;
            }
            
            authenticated = true;
            sendMessage(ThirdPartyApiMessageBuilder.createSuccess(message.getMessageId()));
            thirdPartyApiWebSocketService.onThirdPartyApiAuthenticated(this);
            logger.info("ThirdPartyApi [{}]: authenticated successfully", sid);
            return true;
            
        } catch (Exception e) {
            sendAuthenticationError(message.getMessageId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 发送认证失败错误消息
     */
    private void sendAuthenticationError(String messageId, String reason) throws IOException {
        String logPrefix = buildLogPrefix();
        logger.warn("{}: authentication failed - {}", logPrefix, reason);
        
        sendError(messageId, "Authentication failed: " + reason);
        authenticated = false;
    }

    /**
     * 发送消息（带日志记录）
     * 
     * @param message 要发送的消息对象
     * @throws IOException 如果发送失败
     */
    public void sendMessage(IMessage message) throws IOException {
        String messageString = objectMapper.writeValueAsString(message);
        sendMessage(messageString);
    }

    /**
     * 发送消息（不记录日志）
     * 
     * 适用于高频消息（如心跳响应）以避免日志膨胀
     * 
     * @param message 要发送的消息对象
     * @throws IOException 如果发送失败
     */
    public void sendMessageWithoutLog(IMessage message) throws IOException {
        String messageString = objectMapper.writeValueAsString(message);
        sendMessageWithoutLog(messageString);
    }

    /**
     * 发送消息字符串（不记录日志）
     * 
     * @param messageString 要发送的消息字符串
     * @throws IOException 如果发送失败
     */
    public void sendMessageWithoutLog(String messageString) throws IOException {
        this.session.getBasicRemote().sendText(messageString);
    }

    /**
     * 发送消息（带调试日志记录）
     * 
     * 消息内容会被截断以避免日志过大
     * 
     * @param messageString 要发送的消息字符串
     * @throws IOException 如果发送失败
     */
    public void sendMessage(String messageString) throws IOException {
        sendMessageWithoutLog(messageString);
        
        if (logger.isDebugEnabled()) {
            String logMessage = truncateMessage(messageString);
            logger.debug("{}: sending message: {}", buildLogPrefix(), logMessage);
        }
    }

    /**
     * 检查WebSocket连接是否处于打开状态
     * 
     * @return 如果连接打开返回true，否则返回false
     */
    public boolean isOpen() {
        return session.isOpen();
    }
}
