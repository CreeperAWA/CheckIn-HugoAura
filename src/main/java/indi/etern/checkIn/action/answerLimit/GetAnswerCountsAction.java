package indi.etern.checkIn.action.answerLimit;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.service.dao.AnswerLimitService;

import java.time.LocalDateTime;

@Action("getAnswerCounts")
public class GetAnswerCountsAction extends BaseAction<GetAnswerCountsAction.Input, GetAnswerCountsAction.SuccessOutput> {
    private final AnswerLimitService answerLimitService;
    
    public GetAnswerCountsAction(AnswerLimitService answerLimitService) {
        this.answerLimitService = answerLimitService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, SuccessOutput> context) {
        context.requirePermission("answerLimit.view.count");
        final Input input = context.getInput();
        
        int count = answerLimitService.getAnswerCount(input.qq);
        LocalDateTime firstAnswerTime = answerLimitService.getFirstAnswerTime(input.qq);
        LocalDateTime lastAnswerTime = answerLimitService.getLastAnswerTime(input.qq);
        
        context.resolve(new SuccessOutput(input.qq, count, firstAnswerTime, lastAnswerTime));
    }
    
    public record Input(String qq) implements InputData {
    }
    
    public record SuccessOutput(String qq, int count, LocalDateTime firstAnswerTime, LocalDateTime lastAnswerTime) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
