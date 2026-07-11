package indi.etern.checkIn.action.answerLimit;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.answerLimit.AnswerLimitWhitelist;
import indi.etern.checkIn.service.dao.AnswerLimitService;

import java.util.List;

@Action("getAnswerLimitWhitelist")
public class GetAnswerLimitWhitelistAction extends BaseAction<NullInput, GetAnswerLimitWhitelistAction.SuccessOutput> {
    private final AnswerLimitService answerLimitService;
    
    public GetAnswerLimitWhitelistAction(AnswerLimitService answerLimitService) {
        this.answerLimitService = answerLimitService;
    }
    
    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("answerLimit.viewWhitelist");
        final List<AnswerLimitWhitelist> whitelist = answerLimitService.getAllWhitelist();
        context.resolve(new SuccessOutput(whitelist));
    }
    
    public record SuccessOutput(List<AnswerLimitWhitelist> whitelist) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
