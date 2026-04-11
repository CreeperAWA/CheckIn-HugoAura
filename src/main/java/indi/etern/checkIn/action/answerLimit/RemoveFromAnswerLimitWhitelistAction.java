package indi.etern.checkIn.action.answerLimit;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.service.dao.AnswerLimitService;

@Action("removeFromAnswerLimitWhitelist")
public class RemoveFromAnswerLimitWhitelistAction extends BaseAction<RemoveFromAnswerLimitWhitelistAction.Input, OutputData> {
    private final AnswerLimitService answerLimitService;
    
    public RemoveFromAnswerLimitWhitelistAction(AnswerLimitService answerLimitService) {
        this.answerLimitService = answerLimitService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("answerLimit.manage.whitelist");
        final Input input = context.getInput();
        
        answerLimitService.removeFromWhitelist(input.qq);
        context.resolve(MessageOutput.success("移除成功"));
    }
    
    public record Input(String qq) implements InputData {
    }
}
