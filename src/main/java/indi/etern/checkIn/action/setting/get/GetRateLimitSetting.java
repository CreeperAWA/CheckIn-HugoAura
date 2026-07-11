package indi.etern.checkIn.action.setting.get;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.rateLimit.RateLimitRule;
import indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist;
import indi.etern.checkIn.service.dao.RateLimitService;

import java.util.List;
import java.util.Map;

@Action("getRateLimitSetting")
public class GetRateLimitSetting extends BaseAction<NullInput, GetRateLimitSetting.SuccessOutput> {
    public record SuccessOutput(Map<String, Object> data) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
    
    private final RateLimitService rateLimitService;
    
    public GetRateLimitSetting(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }
    
    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("setting.getAdvance");
        
        List<RateLimitRule> rules = rateLimitService.getAllRules();
        List<RateLimitWhitelist> whitelist = rateLimitService.getAllWhitelistItems();
        
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("rules", rules);
        data.put("whitelist", whitelist);
        
        context.resolve(new SuccessOutput(data));
    }
}
