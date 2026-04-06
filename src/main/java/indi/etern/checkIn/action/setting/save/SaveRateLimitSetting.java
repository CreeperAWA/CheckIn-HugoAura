package indi.etern.checkIn.action.setting.save;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.rateLimit.RateLimitRule;
import indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist;
import indi.etern.checkIn.service.dao.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Action("saveRateLimitSetting")
public class SaveRateLimitSetting extends BaseAction<SaveRateLimitSetting.Input, SaveRateLimitSetting.SuccessOutput> {
    public record Input(Map<String, Object> data) implements InputData {}
    public record SuccessOutput(boolean success) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
    
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public SaveRateLimitSetting(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    @Override
    public void execute(ExecuteContext<Input, SuccessOutput> context) {
        final Input input = context.getInput();
        context.requirePermission("save advance setting");
        
        try {
            Map<String, Object> data = input.data();
            
            if (data != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rulesData = (List<Map<String, Object>>) data.get("rules");
                
                if (rulesData != null) {
                    List<RateLimitRule> rules = validateAndConvertRules(rulesData);
                    rateLimitService.saveRules(rules);
                }
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> createdWhitelistItems =
                        (List<Map<String, Object>>) data.get("createdWhitelistItems");
                @SuppressWarnings("unchecked")
                List<String> deletedWhitelistIds = (List<String>) data.get("deletedWhitelistIds");
                
                if (createdWhitelistItems != null && !createdWhitelistItems.isEmpty()) {
                    for (Map<String, Object> itemData : createdWhitelistItems) {
                        RateLimitWhitelist item = new RateLimitWhitelist();
                        item.setId(UUID.randomUUID().toString());
                        item.setDimension(RateLimitWhitelist.WhitelistDimension.valueOf(
                                (String) itemData.get("dimension")));
                        item.setWhitelistValue((String) itemData.get("value"));
                        item.setDescription((String) itemData.get("description"));
                        rateLimitService.saveWhitelistItem(item);
                    }
                }
                
                if (deletedWhitelistIds != null && !deletedWhitelistIds.isEmpty()) {
                    for (String id : deletedWhitelistIds) {
                        rateLimitService.deleteWhitelistItem(id);
                    }
                }
            }
            
            context.resolve(new SuccessOutput(true));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("限流规则配置无效：" + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("保存限流设置失败：" + e.getMessage(), e);
        }
    }
    
    private List<RateLimitRule> validateAndConvertRules(List<Map<String, Object>> rulesData) {
        for (int i = 0; i < rulesData.size(); i++) {
            Map<String, Object> ruleData = rulesData.get(i);
            String ruleIndex = "规则 #" + (i + 1);
            
            if (ruleData.get("dimension") == null) {
                throw new IllegalArgumentException(ruleIndex + "：限流维度不能为空");
            }
            
            if (ruleData.get("timeWindowSeconds") == null) {
                throw new IllegalArgumentException(ruleIndex + "：时间窗口不能为空");
            }
            int timeWindowSeconds = ((Number) ruleData.get("timeWindowSeconds")).intValue();
            if (timeWindowSeconds <= 0) {
                throw new IllegalArgumentException(ruleIndex + "：时间窗口必须为正整数（单位：秒）");
            }
            if (timeWindowSeconds > 86400) {
                throw new IllegalArgumentException(ruleIndex + "：时间窗口不能超过 24 小时（86400 秒）");
            }
            
            if (ruleData.get("maxRequests") == null) {
                throw new IllegalArgumentException(ruleIndex + "：最大请求数不能为空");
            }
            int maxRequests = ((Number) ruleData.get("maxRequests")).intValue();
            if (maxRequests <= 0) {
                throw new IllegalArgumentException(ruleIndex + "：最大请求数必须为正整数");
            }
            if (maxRequests > 100000) {
                throw new IllegalArgumentException(ruleIndex + "：最大请求数不能超过 100000");
            }
            
            if (ruleData.get("responseStrategy") == null) {
                throw new IllegalArgumentException(ruleIndex + "：响应策略不能为空");
            }
            String responseStrategy = (String) ruleData.get("responseStrategy");
            if ("CUSTOM_MESSAGE".equals(responseStrategy)) {
                Object customMessage = ruleData.get("customMessage");
                if (customMessage != null && customMessage.toString().trim().isEmpty()) {
                    throw new IllegalArgumentException(ruleIndex + "：自定义提示信息不能为空");
                }
            }
            
            if (ruleData.get("baseDelayMs") != null) {
                int baseDelayMs = ((Number) ruleData.get("baseDelayMs")).intValue();
                if (baseDelayMs < 0) {
                    throw new IllegalArgumentException(ruleIndex + "：基础延迟不能为负数");
                }
                if (baseDelayMs > 60000) {
                    throw new IllegalArgumentException(ruleIndex + "：基础延迟不能超过 60000 毫秒");
                }
            }
            
            if (ruleData.get("priority") != null) {
                int priority = ((Number) ruleData.get("priority")).intValue();
                if (priority < 0 || priority > 100) {
                    throw new IllegalArgumentException(ruleIndex + "：优先级必须在 0-100 之间");
                }
            }
        }
        
        return objectMapper.convertValue(rulesData, new TypeReference<List<RateLimitRule>>() {});
    }
}
