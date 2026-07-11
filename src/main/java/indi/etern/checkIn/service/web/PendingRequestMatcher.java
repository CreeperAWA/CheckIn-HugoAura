package indi.etern.checkIn.service.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;

/**
 * 待处理请求匹配器
 * 
 * 提供线程安全的请求匹配功能，支持：
 * 1. 优先通过MessageID精确匹配
 * 2. 降级通过业务键（如QQ号）模糊匹配
 * 3. 原子性的查找和删除操作
 *
 * @param <T> 请求类型
 */
public class PendingRequestMatcher<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(PendingRequestMatcher.class);
    
    private final Map<String, T> pendingRequests;
    private final Function<T, String> messageIdExtractor;
    private final Function<T, String> businessKeyExtractor;
    private final String requestTypeName;
    
    public PendingRequestMatcher(
            Map<String, T> pendingRequests,
            Function<T, String> messageIdExtractor,
            Function<T, String> businessKeyExtractor,
            String requestTypeName) {
        this.pendingRequests = pendingRequests;
        this.messageIdExtractor = messageIdExtractor;
        this.businessKeyExtractor = businessKeyExtractor;
        this.requestTypeName = requestTypeName;
    }
    
    /**
     * 匹配并移除请求
     * 
     * 匹配策略：
     * 1. 优先通过messageId精确匹配
     * 2. 如果失败，通过businessKey降级匹配
     * 3. 使用synchronized确保查找和删除的原子性
     *
     * @param messageId 响应中的MessageID
     * @param businessKey 响应中的业务键（如QQ号）
     * @param additionalLogInfo 附加日志信息（如状态等），可为null
     * @return 匹配到的请求，如果未找到返回null
     */
    public T matchAndRemove(String messageId, String businessKey, String additionalLogInfo) {
        T request = pendingRequests.remove(messageId);
        
        if (request != null) {
            return request;
        }
        
        return matchByBusinessKey(messageId, businessKey, additionalLogInfo);
    }
    
    private T matchByBusinessKey(String responseMessageId, String businessKey, String additionalLogInfo) {
        synchronized (pendingRequests) {
            String matchedKey = null;
            for (Map.Entry<String, T> entry : pendingRequests.entrySet()) {
                String requestBusinessKey = businessKeyExtractor.apply(entry.getValue());
                if (businessKey.equals(requestBusinessKey)) {
                    matchedKey = entry.getKey();
                    break;
                }
            }
            if (matchedKey != null) {
                T request = pendingRequests.remove(matchedKey);
                String requestMessageId = messageIdExtractor.apply(request);
                logger.warn("{} - MessageID mismatch, fallback to business key lookup. " +
                    "Original messageId: {}, response messageId: {}, businessKey: {}{}",
                    requestTypeName, requestMessageId, responseMessageId, businessKey,
                    additionalLogInfo != null ? ", " + additionalLogInfo : "");
                return request;
            }
            
            logger.warn("{} - No pending request found for messageId: {} or businessKey: {}. " +
                "Response may be delayed or duplicate.{}",
                requestTypeName, responseMessageId, businessKey,
                additionalLogInfo != null ? " " + additionalLogInfo : "");
            return null;
        }
    }
}
