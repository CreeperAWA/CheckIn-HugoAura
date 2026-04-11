package indi.etern.checkIn.action.blacklist;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.user.User;
import indi.etern.checkIn.service.dao.BlacklistService;

import java.util.Optional;

@Action("removeFromBlacklist")
public class RemoveFromBlacklistAction extends BaseAction<RemoveFromBlacklistAction.Input, OutputData> {
    private final BlacklistService blacklistService;
    
    public RemoveFromBlacklistAction(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("blacklist.manage");
        final Input input = context.getInput();
        
        if (!blacklistService.isBlacklisted(input.targetId)) {
            context.resolve(MessageOutput.error("该ID不在黑名单中"));
            return;
        }
        
        Long operatorQQ = Optional.ofNullable(context.getCurrentUser()).map(User::getQQNumber).orElse(null);
        blacklistService.removeFromBlacklist(input.targetId, operatorQQ);
        
        context.resolve(new SuccessOutput());
    }
    
    public record Input(String targetId) implements InputData {
    }
    
    public record SuccessOutput() implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
