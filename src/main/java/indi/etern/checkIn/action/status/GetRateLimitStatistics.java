package indi.etern.checkIn.action.status;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.service.dao.RateLimitLogService;

import java.time.LocalDateTime;
import java.util.Map;

@Action("getRateLimitStatistics")
public class GetRateLimitStatistics extends BaseAction<NullInput, GetRateLimitStatistics.SuccessOutput> {
    public record SuccessOutput(Map<String, Object> data) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
    
    private final RateLimitLogService logService;
    
    public GetRateLimitStatistics(RateLimitLogService logService) {
        this.logService = logService;
    }
    
    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("get advance setting");
        
        LocalDateTime startTime = LocalDateTime.now().minusHours(24);
        Map<String, Object> statistics = logService.getStatistics(startTime);
        
        var recentLogs = logService.getLatestLogs(50);
        statistics.put("recentLogs", recentLogs);
        
        context.resolve(new SuccessOutput(statistics));
    }
}
