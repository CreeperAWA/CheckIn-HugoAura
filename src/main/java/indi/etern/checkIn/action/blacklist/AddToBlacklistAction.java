package indi.etern.checkIn.action.blacklist;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.blacklist.Blacklist;
import indi.etern.checkIn.entities.user.User;
import indi.etern.checkIn.service.dao.BlacklistService;

import java.util.Optional;

@Action("addToBlacklist")
public class AddToBlacklistAction extends BaseAction<AddToBlacklistAction.Input, OutputData> {
    private final BlacklistService blacklistService;
    
    public AddToBlacklistAction(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("blacklist.manage");
        final Input input = context.getInput();
        
        if (blacklistService.isBlacklisted(input.targetId)) {
            context.resolve(MessageOutput.error("该ID已在黑名单中"));
            return;
        }
        
        Long operatorQQ = Optional.ofNullable(context.getCurrentUser()).map(User::getQQNumber).orElse(null);
        Blacklist blacklist = blacklistService.addToBlacklist(input.targetId, input.reason, operatorQQ);
        
        context.resolve(new SuccessOutput(blacklist));
    }
    
    public record Input(String targetId, String reason) implements InputData {
    }
    
    public record SuccessOutput(Blacklist blacklist) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
