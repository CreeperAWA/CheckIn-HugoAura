package indi.etern.checkIn.api.robotWebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.api.webSocket.JsonRawMessage;
import indi.etern.checkIn.api.webSocket.Message;
import indi.etern.checkIn.api.webSocket.interfaces.IMessage;
import indi.etern.checkIn.auth.JwtTokenProvider;
import indi.etern.checkIn.entities.user.User;
import indi.etern.checkIn.service.web.RobotWebSocketService;
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

@Component
@ServerEndpoint("/checkIn/api/websocket/{sid}")
@ConditionalOnWebApplication
public class RobotConnector {
    public static final CopyOnWriteArraySet<RobotConnector> CONNECTORS = new CopyOnWriteArraySet<>();
    public static final Logger logger = LoggerFactory.getLogger(RobotConnector.class);
    private static RobotWebSocketService robotWebSocketService;
    private static JwtTokenProvider jwtTokenProvider;
    private static ObjectMapper objectMapper;
    private final int BUFFER_SIZE = 64 * 1024;
    private final int LOG_TRUNCATE_SIZE = 512;
    private Session session;
    @Getter
    private String sid = "";
    private boolean authenticated = false;

    @Autowired
    public void setRobotWebSocketService(RobotWebSocketService robotWebSocketService) {
        RobotConnector.robotWebSocketService = robotWebSocketService;
    }

    @Autowired
    public void setJwtTokenProvider(JwtTokenProvider jwtTokenProvider) {
        RobotConnector.jwtTokenProvider = jwtTokenProvider;
    }

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        RobotConnector.objectMapper = objectMapper;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        this.session = session;
        session.setMaxTextMessageBufferSize(BUFFER_SIZE);
        session.setMaxBinaryMessageBufferSize(0);
        CONNECTORS.add(this);
        this.sid = sid;
        logger.info("Robot sid_{}:connected", sid);
    }

    @OnError
    public void onError(Throwable throwable) {
        if (throwable instanceof IOException) {
            logger.trace("Robot websocket connector error, may cause by normal disconnection", throwable);
        } else {
            logger.error("Robot websocket connector error", throwable);
        }
    }

    @SneakyThrows
    @OnClose
    public void onClose() {
        CONNECTORS.remove(this);
        logger.info("Robot sid_{}:close", sid);
    }

    private final Map<String, PartRawMessageProcessor> partMessageMap = new HashMap<>();

    private record PartMessageData(List<String> messageIds, String partId, String messagePart) {
    }

    @OnMessage
    public void onMessage(String message) {
        try {
            JsonRawMessage contextJsonMessage = objectMapper.readValue(message, JsonRawMessage.class);
            if (!checkToken(contextJsonMessage)) {
                return;
            }
            if (!authenticated) {
                return;
            }
            String contextId = contextJsonMessage.getMessageId();

            final String typeName = contextJsonMessage.getType().getName();
            switch (typeName) {
                case "ping" -> this.sendMessageWithoutLog(Message.of("pong", contextId, null));
                case "partMessage" -> {
                    PartMessageData partMessageData = objectMapper.readValue(contextJsonMessage.getData(), PartMessageData.class);
                    PartRawMessageProcessor partRawMessageProcessor;
                    String partId;
                    if (partMessageData.messageIds != null) {
                        partRawMessageProcessor = new PartRawMessageProcessor(partMessageData.messageIds);
                        partMessageMap.put(contextId, partRawMessageProcessor);
                        partId = contextId;
                        sendMessage("{\"type\":\"success\",\"messageId\":\"" + contextId + "\"}");
                    } else if (partMessageData.partId != null && partMessageData.messagePart != null) {
                        partId = partMessageData.partId;
                        partRawMessageProcessor = partMessageMap.get(partId);
                        partRawMessageProcessor.put(contextId, partMessageData.messagePart);
                    } else {
                        sendMessage("{\"type\":\"error\",\"message\":\"not supported message format\",\"messageId\":\"" + contextId + "\"}");
                        return;
                    }
                    if (partRawMessageProcessor.isComplete()) {
                        onMessage(partRawMessageProcessor.toString());
                        partMessageMap.remove(partId);
                    }
                }
                case "qq_verify_response" -> {
                    robotWebSocketService.handleQQVerifyResponse(this, contextJsonMessage);
                }
                default -> {
                    String logMessage = message;
                    if (logMessage.length() > LOG_TRUNCATE_SIZE) {
                        logMessage = logMessage.substring(0, LOG_TRUNCATE_SIZE) + "\n=========(truncated)=========";
                    }
                    logger.debug("Robot sid_{}:{}", sid, logMessage);
                }
            }
        } catch (Exception e) {
            try {
                logger.error("{}:{}", e.getClass().getName(), e.getMessage());
                String messageId = objectMapper.readValue(message, HashMap.class).get("messageId").toString();
                sendError(messageId, e.getClass().getSimpleName() + ":" + e.getMessage());
            } catch (IOException exception) {
                logger.error("while sending error message (caused by \"{}\") to robot sid_{}:{}", message, sid, exception.getMessage());
                exception.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    private void sendError(String contextId, String data) throws IOException {
        Message<String> message = Message.error(contextId, data);
        sendMessage(message);
    }

    private record TokenMessage(String token) {
    }

    private boolean checkToken(JsonRawMessage message) throws IOException {
        if (message.getType().equals(Message.Type.of("token"))) {
            final TokenMessage tokenMessage = objectMapper.readValue(message.getData(), TokenMessage.class);
            try {
                User user = jwtTokenProvider.getUser(tokenMessage.token);
                authenticated = true;
                sendMessage("{\"type\":\"success\",\"messageId\":\"" + message.getMessageId() + "\"}");
                robotWebSocketService.onRobotAuthenticated(this);
                return true;
            } catch (Exception e) {
                sendError(message.getMessageId(), "Authentication failed: " + e.getMessage());
                authenticated = false;
                return false;
            }
        } else if (authenticated) {
            return true;
        } else {
            sendMessage("{\"type\":\"error\",\"message\":\"Not authenticated\",\"messageId\":\"" + (message.getMessageId() != null ? message.getMessageId() : "") + "\"}");
            return false;
        }
    }

    public void sendMessage(IMessage message) throws IOException {
        final String messageString = objectMapper.writeValueAsString(message);
        sendMessage(messageString);
    }

    public void sendMessageWithoutLog(IMessage message) throws IOException {
        final String messageString = objectMapper.writeValueAsString(message);
        sendMessageWithoutLog(messageString);
    }

    public void sendMessageWithoutLog(String messageString) throws IOException {
        this.session.getBasicRemote().sendText(messageString);
    }

    public void sendMessage(String messageString) throws IOException {
        sendMessageWithoutLog(messageString);
        if (logger.isDebugEnabled()) {
            if (messageString.length() > LOG_TRUNCATE_SIZE)
                messageString = messageString.substring(0, LOG_TRUNCATE_SIZE) + "\n=========(truncated)=========";
            logger.debug("Robot webSocket to sid_{}:{}", sid, messageString);
        }
    }

    public boolean isOpen() {
        return session.isOpen();
    }
}
