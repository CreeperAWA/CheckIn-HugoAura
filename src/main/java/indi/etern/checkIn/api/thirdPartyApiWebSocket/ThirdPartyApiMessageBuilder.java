package indi.etern.checkIn.api.thirdPartyApiWebSocket;

import indi.etern.checkIn.api.webSocket.Message;
import indi.etern.checkIn.utils.UUIDv7;

import java.util.HashMap;
import java.util.Map;

/**
 * 第三方API消息构建器
 * 
 * 提供便捷的消息创建方法，统一消息格式
 * 所有方法返回标准化的Message对象，可直接通过WebSocket发送
 */
public final class ThirdPartyApiMessageBuilder {
    
    private ThirdPartyApiMessageBuilder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    /**
     * 创建成功响应消息
     * 
     * @param messageId 消息ID，用于关联请求和响应
     * @return 成功响应消息
     */
    public static Message<?> createSuccess(String messageId) {
        return Message.of(ThirdPartyApiMessageTypes.SUCCESS, messageId, null);
    }
    
    /**
     * 创建错误响应消息
     * 
     * @param messageId 消息ID，用于关联请求和响应
     * @param errorMessage 错误描述信息
     * @return 错误响应消息
     */
    public static Message<?> createError(String messageId, String errorMessage) {
        return Message.error(messageId, errorMessage);
    }
    
    /**
     * 创建未认证错误消息
     * 
     * @param messageId 消息ID
     * @return 未认证错误消息
     */
    public static Message<?> createNotAuthenticatedError(String messageId) {
        return createError(
            messageId != null ? messageId : "",
            "Not authenticated"
        );
    }
    
    /**
     * 创建认证失败错误消息
     * 
     * @param messageId 消息ID
     * @param reason 认证失败原因
     * @return 认证失败错误消息
     */
    public static Message<?> createAuthFailedError(String messageId, String reason) {
        return createError(messageId, "Authentication failed: " + reason);
    }
    
    /**
     * 创建心跳响应消息（PONG）
     * 
     * @param messageId 对应的PING消息ID
     * @return PONG消息
     */
    public static Message<?> createPong(String messageId) {
        return Message.of(ThirdPartyApiMessageTypes.PONG, messageId, null);
    }
    
    /**
     * 创建QQ验证询问消息
     * 
     * 用于向第三方API询问是否需要验证指定QQ号
     * 
     * @param qq QQ号码
     * @param messageId 消息ID（由调用方传入，确保请求跟踪一致）
     * @return 验证询问消息
     */
    public static Message<Map<String, Object>> createQQVerifyCheck(String qq, String messageId) {
        Map<String, Object> data = new HashMap<>();
        data.put("qq", qq);
        return Message.of(ThirdPartyApiMessageTypes.QQ_VERIFY_CHECK, messageId, data);
    }
    
    /**
     * 创建QQ验证请求消息
     * 
     * 用于请求第三方API执行QQ验证
     * 
     * @param qq QQ号码
     * @param verifyContent 验证内容（如验证码、验证链接等）
     * @param messageId 消息ID（由调用方传入，确保请求跟踪一致）
     * @return 验证请求消息
     */
    public static Message<Map<String, Object>> createQQVerifyRequest(String qq, String verifyContent, String messageId) {
        Map<String, Object> data = new HashMap<>();
        data.put("qq", qq);
        data.put("verify_content", verifyContent);
        return Message.of(ThirdPartyApiMessageTypes.QQ_VERIFY_REQUEST, messageId, data);
    }
    
    /**
     * 创建黑名单全量推送消息
     * 
     * @param blacklistList 黑名单列表，每个元素包含id、qq、reason、created_at字段
     * @return 黑名单全量推送消息
     */
    public static Message<Map<String, Object>> createBlacklistFull(java.util.List<Map<String, Object>> blacklistList) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", blacklistList);
        return Message.of(ThirdPartyApiMessageTypes.BLACKLIST_FULL, UUIDv7.randomUUID().toString(), data);
    }
    
    /**
     * 创建黑名单新增通知消息
     * 
     * @param id 黑名单记录ID
     * @param qq QQ号码
     * @param reason 封禁原因
     * @param createdAt 创建时间数组
     * @return 黑名单新增通知消息
     */
    public static Message<Map<String, Object>> createBlacklistAdd(String id, String qq, String reason, int[] createdAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("qq", qq);
        data.put("reason", reason);
        data.put("created_at", createdAt);
        return Message.of(ThirdPartyApiMessageTypes.BLACKLIST_ADD, UUIDv7.randomUUID().toString(), data);
    }
    
    /**
     * 创建黑名单移除通知消息
     * 
     * @param id 黑名单记录ID
     * @param qq QQ号码
     * @return 黑名单移除通知消息
     */
    public static Message<Map<String, Object>> createBlacklistRemove(String id, String qq) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("qq", qq);
        return Message.of(ThirdPartyApiMessageTypes.BLACKLIST_REMOVE, UUIDv7.randomUUID().toString(), data);
    }
    
    /**
     * 创建自定义通知消息
     * 
     * @param type 通知类型
     * @param data 通知数据
     * @return 通知消息
     */
    public static Message<Map<String, Object>> createNotification(String type, Map<String, Object> data) {
        return Message.of(type, UUIDv7.randomUUID().toString(), data);
    }
    
    /**
     * 创建考试记录查询响应消息
     * 
     * @param messageId 消息ID，与查询请求保持一致
     * @param data 响应数据，包含qq和records字段
     * @return 考试记录查询响应消息
     */
    public static Message<Map<String, Object>> createExamRecordsResponse(String messageId, Map<String, Object> data) {
        return Message.of(ThirdPartyApiMessageTypes.EXAM_RECORDS_RESPONSE, messageId, data);
    }
}
