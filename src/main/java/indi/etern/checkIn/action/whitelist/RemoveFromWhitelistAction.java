package indi.etern.checkIn.action.whitelist;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.user.User;
import indi.etern.checkIn.service.dao.WhitelistService;

import java.util.Optional;

@Action("removeFromWhitelist")
public class RemoveFromWhitelistAction extends BaseAction<RemoveFromWhitelistAction.Input, OutputData> {
    private final WhitelistService whitelistService;
    
    public RemoveFromWhitelistAction(WhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("thirdPartyApi.manageWhitelist");
        final Input input = context.getInput();
        
        if (!whitelistService.isWhitelisted(input.targetId)) {
            context.resolve(MessageOutput.error("该ID不在白名单中"));
            return;
        }
        
        Long operatorQQ = Optional.ofNullable(context.getCurrentUser()).map(User::getQQNumber).orElse(null);
        whitelistService.removeFromWhitelist(input.targetId, operatorQQ);
        
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
