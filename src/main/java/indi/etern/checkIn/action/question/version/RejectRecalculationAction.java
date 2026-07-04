package indi.etern.checkIn.action.question.version;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.service.question.ScoreRecalculationService;
import jakarta.annotation.Nonnull;

@Action("rejectRecalculation")
public class RejectRecalculationAction extends BaseAction<RejectRecalculationAction.Input, OutputData> {
    
    private final ScoreRecalculationService recalculationService;
    
    public record Input(@Nonnull String logId) implements InputData {}
    public record SuccessOutput() implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
    public record ErrorOutput(String message) implements OutputData {
        @Override
        public Result result() {
            return Result.ERROR;
        }
    }
    
    public RejectRecalculationAction(ScoreRecalculationService recalculationService) {
        this.recalculationService = recalculationService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("questionVersion.approveRecalculation");
        try {
            recalculationService.rejectRecalculation(
                    context.getInput().logId(),
                    context.getCurrentUser().getQQNumber());
            context.resolve(new SuccessOutput());
        } catch (Exception e) {
            context.resolve(new ErrorOutput(e.getMessage()));
        }
    }
}
