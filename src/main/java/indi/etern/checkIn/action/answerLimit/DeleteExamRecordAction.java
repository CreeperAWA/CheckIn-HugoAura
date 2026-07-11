package indi.etern.checkIn.action.answerLimit;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.service.dao.AnswerLimitService;

@Action("deleteExamRecord")
public class DeleteExamRecordAction extends BaseAction<DeleteExamRecordAction.Input, OutputData> {
    private final AnswerLimitService answerLimitService;
    
    public DeleteExamRecordAction(AnswerLimitService answerLimitService) {
        this.answerLimitService = answerLimitService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("answerLimit.manageRecord");
        final Input input = context.getInput();
        
        answerLimitService.deleteExamRecord(input.examId);
        context.resolve(MessageOutput.success("删除成功"));
    }
    
    public record Input(String examId) implements InputData {
    }
}
