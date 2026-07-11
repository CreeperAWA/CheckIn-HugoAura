package indi.etern.checkIn.action.answerLimit;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.exam.ExamData;
import indi.etern.checkIn.service.dao.AnswerLimitService;

import java.util.List;

@Action("getAnswerLimitExamRecordsByQQ")
public class GetExamRecordsByQQAction extends BaseAction<GetExamRecordsByQQAction.Input, GetExamRecordsByQQAction.SuccessOutput> {
    private final AnswerLimitService answerLimitService;
    
    public GetExamRecordsByQQAction(AnswerLimitService answerLimitService) {
        this.answerLimitService = answerLimitService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, SuccessOutput> context) {
        context.requirePermission("answerLimit.viewCount");
        final Input input = context.getInput();
        
        List<ExamData> examRecords = answerLimitService.getExamRecordsByQQ(input.qq);
        context.resolve(new SuccessOutput(examRecords));
    }
    
    public record Input(String qq) implements InputData {
    }
    
    public record SuccessOutput(List<ExamData> examRecords) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
