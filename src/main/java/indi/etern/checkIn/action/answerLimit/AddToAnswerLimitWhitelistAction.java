package indi.etern.checkIn.action.answerLimit;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.answerLimit.AnswerLimitWhitelist;
import indi.etern.checkIn.entities.user.User;
import indi.etern.checkIn.service.dao.AnswerLimitService;

import java.util.Optional;

@Action("addToAnswerLimitWhitelist")
public class AddToAnswerLimitWhitelistAction extends BaseAction<AddToAnswerLimitWhitelistAction.Input, OutputData> {
    private final AnswerLimitService answerLimitService;
    
    public AddToAnswerLimitWhitelistAction(AnswerLimitService answerLimitService) {
        this.answerLimitService = answerLimitService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("answerLimit.manage.whitelist");
        final Input input = context.getInput();
        
        if (answerLimitService.isInWhitelist(input.qq)) {
            context.resolve(MessageOutput.error("该QQ号已在白名单中"));
            return;
        }
        
        Long operatorQQ = Optional.ofNullable(context.getCurrentUser()).map(User::getQQNumber).orElse(null);
        AnswerLimitWhitelist whitelist = answerLimitService.addToWhitelist(input.qq, input.reason, operatorQQ);
        
        context.resolve(new SuccessOutput(whitelist));
    }
    
    public record Input(String qq, String reason) implements InputData {
    }
    
    public record SuccessOutput(AnswerLimitWhitelist whitelist) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
