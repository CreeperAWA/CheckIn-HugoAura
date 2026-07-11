package indi.etern.checkIn.action.answerLimit;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.service.dao.AnswerLimitService;

@Action("getAnswerLimitSetting")
public class GetAnswerLimitSettingAction extends BaseAction<NullInput, GetAnswerLimitSettingAction.SuccessOutput> {
    private final AnswerLimitService answerLimitService;
    
    public GetAnswerLimitSettingAction(AnswerLimitService answerLimitService) {
        this.answerLimitService = answerLimitService;
    }
    
    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("answerLimit.viewSetting");
        int maxCount = answerLimitService.getMaxAnswerCount();
        context.resolve(new SuccessOutput(maxCount));
    }
    
    public record SuccessOutput(int maxCount) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
