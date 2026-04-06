package indi.etern.checkIn.action.setting.save;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Action("saveRateLimitSetting")
public class SaveRateLimitSetting extends BaseAction<SaveRateLimitSetting.Input, SaveRateLimitSetting.SuccessOutput> {
    public record Input(
            List<Map<String, Object>> rules,
            List<Map<String, Object>> createdWhitelistItems,
            List<String> deletedWhitelistIds
    ) implements InputData {}
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
    
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void execute(ExecuteContext<Input, SuccessOutput> context) {
        final Input input = context.getInput();
        context.requirePermission("save advance setting");
        
        if (input.rules() != null && !input.rules().isEmpty()) {
            List<RateLimitRule> rules = validateAndConvertRules(input.rules());
            rateLimitService.saveRules(rules);
        }
        
        if ((input.createdWhitelistItems() != null && !input.createdWhitelistItems().isEmpty()) ||
            (input.deletedWhitelistIds() != null && !input.deletedWhitelistIds().isEmpty())) {
            rateLimitService.updateWhitelist(
                    convertWhitelistItems(input.createdWhitelistItems()),
                    input.deletedWhitelistIds()
            );
        }
        
        context.resolve(new SuccessOutput(true));
    }
    
    private List<RateLimitRule> validateAndConvertRules(List<Map<String, Object>> rulesData) {
        List<RateLimitRule> rules = new ArrayList<>();
        
        for (int i = 0; i < rulesData.size(); i++) {
            Map<String, Object> ruleData = rulesData.get(i);
            String ruleIndex = "规则 #" + (i + 1);
            
            validateDimension(ruleData, ruleIndex);
            validateTimeWindow(ruleData, ruleIndex);
            validateMaxRequests(ruleData, ruleIndex);
            validateResponseStrategy(ruleData, ruleIndex);
            validateBaseDelay(ruleData, ruleIndex);
            validatePriority(ruleData, ruleIndex);
            validateLimitDuration(ruleData, ruleIndex);
            
            RateLimitRule rule = objectMapper.convertValue(ruleData, RateLimitRule.class);
            
            if (rule.getId() == null || rule.getId().trim().isEmpty()) {
                rule.setId(UUID.randomUUID().toString());
            }
            
            rules.add(rule);
        }
        
        return rules;
    }
    
    private List<indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist> convertWhitelistItems(List<Map<String, Object>> itemsData) {
        if (itemsData == null || itemsData.isEmpty()) {
            return null;
        }
        
        List<indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist> items = new ArrayList<>();
        for (Map<String, Object> itemData : itemsData) {
            indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist item = new indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist();
            item.setId(UUID.randomUUID().toString());
            item.setDimension(indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist.WhitelistDimension.valueOf(
                    (String) itemData.get("dimension")));
            item.setWhitelistValue(((String) itemData.get("value")).trim());
            item.setDescription(itemData.get("description") != null ? 
                    ((String) itemData.get("description")).trim() : null);
            
            items.add(item);
        }
        
        return items;
    }
    
    private void validateDimension(Map<String, Object> ruleData, String ruleIndex) {
        if (ruleData.get("dimension") == null) {
            throw new IllegalArgumentException(ruleIndex + "：限流维度不能为空");
        }
        try {
            RateLimitRule.RateLimitDimension.valueOf((String) ruleData.get("dimension"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ruleIndex + "：无效的限流维度");
        }
    }
    
    private void validateTimeWindow(Map<String, Object> ruleData, String ruleIndex) {
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
    }
    
    private void validateMaxRequests(Map<String, Object> ruleData, String ruleIndex) {
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
    }
    
    private void validateResponseStrategy(Map<String, Object> ruleData, String ruleIndex) {
        if (ruleData.get("responseStrategy") == null) {
            throw new IllegalArgumentException(ruleIndex + "：响应策略不能为空");
        }
        String responseStrategy = (String) ruleData.get("responseStrategy");
        try {
            RateLimitRule.ResponseStrategy.valueOf(responseStrategy);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ruleIndex + "：无效的响应策略");
        }
        
        if ("CUSTOM_MESSAGE".equals(responseStrategy)) {
            Object customMessage = ruleData.get("customMessage");
            if (customMessage == null || customMessage.toString().trim().isEmpty()) {
                throw new IllegalArgumentException(ruleIndex + "：自定义提示信息不能为空");
            }
        }
    }
    
    private void validateBaseDelay(Map<String, Object> ruleData, String ruleIndex) {
        if (ruleData.get("baseDelayMs") != null) {
            int baseDelayMs = ((Number) ruleData.get("baseDelayMs")).intValue();
            if (baseDelayMs < 0) {
                throw new IllegalArgumentException(ruleIndex + "：基础延迟不能为负数");
            }
            if (baseDelayMs > 60000) {
                throw new IllegalArgumentException(ruleIndex + "：基础延迟不能超过 60000 毫秒");
            }
        }
    }
    
    private void validatePriority(Map<String, Object> ruleData, String ruleIndex) {
        if (ruleData.get("priority") != null) {
            int priority = ((Number) ruleData.get("priority")).intValue();
            if (priority < 0 || priority > 100) {
                throw new IllegalArgumentException(ruleIndex + "：优先级必须在 0-100 之间");
            }
        }
    }
    
    private void validateLimitDuration(Map<String, Object> ruleData, String ruleIndex) {
        if (ruleData.get("limitDurationSeconds") == null) {
            throw new IllegalArgumentException(ruleIndex + "：限流持续时间不能为空");
        }
        int limitDurationSeconds = ((Number) ruleData.get("limitDurationSeconds")).intValue();
        if (limitDurationSeconds <= 0) {
            throw new IllegalArgumentException(ruleIndex + "：限流持续时间必须为正整数（单位：秒）");
        }
        if (limitDurationSeconds > 86400) {
            throw new IllegalArgumentException(ruleIndex + "：限流持续时间不能超过 24 小时（86400 秒）");
        }
    }
}
