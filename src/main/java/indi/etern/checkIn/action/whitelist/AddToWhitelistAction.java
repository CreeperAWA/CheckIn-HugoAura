package indi.etern.checkIn.action.whitelist;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.user.User;
import indi.etern.checkIn.entities.whitelist.Whitelist;
import indi.etern.checkIn.service.dao.WhitelistService;

import java.util.Optional;

@Action("addToWhitelist")
public class AddToWhitelistAction extends BaseAction<AddToWhitelistAction.Input, OutputData> {
    private final WhitelistService whitelistService;
    
    public AddToWhitelistAction(WhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("robotApi.manage.whitelist");
        final Input input = context.getInput();
        
        if (whitelistService.isWhitelisted(input.targetId)) {
            context.resolve(MessageOutput.error("该ID已在白名单中"));
            return;
        }
        
        Long operatorQQ = Optional.ofNullable(context.getCurrentUser()).map(User::getQQNumber).orElse(null);
        Whitelist whitelist = whitelistService.addToWhitelist(input.targetId, input.reason, operatorQQ);
        
        context.resolve(new SuccessOutput(whitelist));
    }
    
    public record Input(String targetId, String reason) implements InputData {
    }
    
    public record SuccessOutput(Whitelist whitelist) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
