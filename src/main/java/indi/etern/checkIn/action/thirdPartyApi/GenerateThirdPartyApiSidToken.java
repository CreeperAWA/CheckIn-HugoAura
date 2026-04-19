package indi.etern.checkIn.action.thirdPartyApi;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.auth.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;

/**
 * 生成第三方API SID Token
 * 
 * 根据提供的SID（Service ID）生成JWT格式的访问令牌
 * 用于第三方API连接时的身份认证
 * 
 * 权限要求：thirdPartyApi.manage.setting
 * 
 * 输入参数：
 * - sid: 第三方API服务标识符（必填，UUID格式）
 * 
 * 输出数据：
 * - 成功：{ "token": "生成的JWT令牌" }
 * - 失败：{ "error": "错误描述" }
 */
@Action("generateThirdPartyApiSidToken")
public class GenerateThirdPartyApiSidToken extends BaseAction<GenerateThirdPartyApiSidToken.Input, GenerateThirdPartyApiSidToken.SuccessOutput> {
    
    private static final Logger logger = LoggerFactory.getLogger(GenerateThirdPartyApiSidToken.class);
    
    /**
     * 输入数据结构
     */
    public record Input(String sid) implements InputData {}

    /**
     * 输出数据结构
     */
    public record SuccessOutput(LinkedHashMap<String, Object> data) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }

    private static JwtTokenProvider jwtTokenProvider;

    @Autowired
    public void setJwtTokenProvider(JwtTokenProvider jwtTokenProvider) {
        GenerateThirdPartyApiSidToken.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void execute(ExecuteContext<Input, SuccessOutput> context) {
        context.requirePermission("thirdPartyApi.manage.setting");
        
        String sid = context.getInput().sid;
        
        if (sid == null || sid.trim().isEmpty()) {
            logger.warn("Failed to generate token: SID is empty");
            context.resolve(createErrorResponse("SID 不能为空"));
            return;
        }
        
        try {
            String trimmedSid = sid.trim();
            logger.info("Generating token for SID: {}", trimmedSid);
            
            String token = jwtTokenProvider.generateThirdPartyApiSidToken(trimmedSid);
            
            logger.info("Token generated successfully for SID: {}", trimmedSid);
            context.resolve(createSuccessResponse(token));
            
        } catch (Exception e) {
            logger.error("Failed to generate token: {}", e.getMessage(), e);
            context.resolve(createErrorResponse("Token 生成失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建成功响应
     */
    private SuccessOutput createSuccessResponse(String token) {
        return new SuccessOutput(new LinkedHashMap<String, Object>() {{
            put("token", token);
        }});
    }
    
    /**
     * 创建错误响应
     */
    private SuccessOutput createErrorResponse(String errorMessage) {
        return new SuccessOutput(new LinkedHashMap<String, Object>() {{
            put("error", errorMessage);
        }});
    }
}
