package indi.etern.checkIn.action.blacklist;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.service.dao.BlacklistService;

@Action("checkBlacklist")
public class CheckBlacklistAction extends BaseAction<CheckBlacklistAction.Input, CheckBlacklistAction.SuccessOutput> {
    private final BlacklistService blacklistService;
    
    public CheckBlacklistAction(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, SuccessOutput> context) {
        final Input input = context.getInput();
        boolean isBlacklisted = blacklistService.isBlacklisted(input.targetId);
        context.resolve(new SuccessOutput(isBlacklisted));
    }
    
    public record Input(String targetId) implements InputData {
    }
    
    public record SuccessOutput(boolean isBlacklisted) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
