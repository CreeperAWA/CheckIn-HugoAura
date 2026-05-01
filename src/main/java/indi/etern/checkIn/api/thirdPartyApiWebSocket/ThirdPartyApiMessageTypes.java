package indi.etern.checkIn.api.thirdPartyApiWebSocket;

/**
 * 第三方API WebSocket消息类型常量
 * 
 * 定义了服务器与第三方API客户端之间通信的所有消息类型
 * 用于统一消息类型的管理，避免硬编码字符串
 * 
 * 消息类型分类：
 * 1. 连接控制类：token、ping、pong、success、error
 * 2. 黑名单管理类：blacklist_full、blacklist_add、blacklist_remove
 * 3. QQ验证类：qq_verify_check、qq_verify_check_response、qq_verify_request、qq_verify_response
 * 4. 通知类：notification_login_success、notification_login_failure 等
 * 5. 分片消息类：partMessage
 */
public final class ThirdPartyApiMessageTypes {
    
    private ThirdPartyApiMessageTypes() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // ========== 连接控制类消息 ==========
    
    /**
     * 认证令牌消息 - 客户端发送token进行身份验证
     */
    public static final String TOKEN = "token";
    
    /**
     * 心跳请求 - 客户端发送以保持连接活跃
     */
    public static final String PING = "ping";
    
    /**
     * 心跳响应 - 服务器响应ping请求
     */
    public static final String PONG = "pong";
    
    /**
     * 成功响应 - 表示操作成功完成
     */
    public static final String SUCCESS = "success";
    
    /**
     * 错误响应 - 表示操作失败，包含错误信息
     */
    public static final String ERROR = "error";
    
    // ========== 黑名单管理类消息 ==========
    
    /**
     * 完整黑名单列表 - 连接建立后服务器推送完整黑名单
     */
    public static final String BLACKLIST_FULL = "blacklist_full";
    
    /**
     * 黑名单新增 - 通知客户端新增黑名单条目
     */
    public static final String BLACKLIST_ADD = "blacklist_add";
    
    /**
     * 黑名单移除 - 通知客户端移除黑名单条目
     */
    public static final String BLACKLIST_REMOVE = "blacklist_remove";
    
    // ========== QQ验证类消息 ==========
    
    /**
     * 验证询问请求 - 服务器询问第三方API是否需要验证该QQ号
     * 数据结构：{ "qq": "QQ号" }
     * 期望响应：qq_verify_check_response
     */
    public static final String QQ_VERIFY_CHECK = "qq_verify_check";
    
    /**
     * 验证询问响应 - 第三方API回复是否需要验证
     * 数据结构：{ "qq": "QQ号", "need_verify": boolean }
     */
    public static final String QQ_VERIFY_CHECK_RESPONSE = "qq_verify_check_response";
    
    /**
     * 验证请求 - 服务器请求第三方API执行QQ验证
     * 数据结构：{ "qq": "QQ号", "verify_content": "验证内容" }
     * 期望响应：qq_verify_response
     */
    public static final String QQ_VERIFY_REQUEST = "qq_verify_request";
    
    /**
     * 验证响应 - 第三方API返回验证结果
     * 数据结构：{ "qq": "QQ号", "status": "状态", "message": "消息" }
     */
    public static final String QQ_VERIFY_RESPONSE = "qq_verify_response";
    
    // ========== 通知类消息 ==========
    
    /**
     * 通知消息 - 各类系统通知
     * 前缀：notification_
     */
    public static final String NOTIFICATION = "notification_";
    
    // ========== 成绩查询类消息 ==========
    
    /**
     * 考试记录查询请求 - 第三方客户端查询用户历次考试成绩
     * 数据结构：{ "qq": "QQ号" }
     * 期望响应：exam_records_response
     */
    public static final String EXAM_RECORDS_QUERY = "exam_records_query";
    
    /**
     * 考试记录查询响应 - 服务端返回用户考试记录列表
     * 数据结构：{ "qq": "QQ号", "records": [...] }
     */
    public static final String EXAM_RECORDS_RESPONSE = "exam_records_response";
    
    /**
     * 考试无效化请求 - 第三方客户端请求无效化某次考试结果
     * 数据结构：{ "paper_id": "试卷ID" }
     * 期望响应：exam_invalidate_response
     */
    public static final String EXAM_INVALIDATE_REQUEST = "exam_invalidate_request";
    
    /**
     * 考试无效化响应 - 服务端返回无效化操作结果
     * 数据结构：{ "paper_id": "试卷ID", "status": "success/failed" }
     */
    public static final String EXAM_INVALIDATE_RESPONSE = "exam_invalidate_response";
    
    // ========== 分片消息类 ==========
    
    /**
     * 分片消息 - 用于传输大消息的分片
     * 初始化分片：{ "messageIds": [...], ... }
     * 后续分片：{ "partId": "分片ID", "messagePart": "分片内容" }
     */
    public static final String PART_MESSAGE = "partMessage";
}
