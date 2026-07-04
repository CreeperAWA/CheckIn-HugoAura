package indi.etern.checkIn.action.question.version;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.service.question.ScoreRecalculationService;
import jakarta.annotation.Nonnull;

@Action("triggerScoreRecalculation")
public class TriggerScoreRecalculationAction extends BaseAction<TriggerScoreRecalculationAction.Input, OutputData> {
    
    private final ScoreRecalculationService recalculationService;
    
    public record Input(@Nonnull String questionId) implements InputData {}
    public record SuccessOutput(String logId) implements OutputData {
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
    
    public TriggerScoreRecalculationAction(ScoreRecalculationService recalculationService) {
        this.recalculationService = recalculationService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("questionVersion.recalculate");
        try {
            String logId = recalculationService.triggerManualRecalculation(
                    context.getInput().questionId(),
                    context.getCurrentUser().getQQNumber());
            context.resolve(new SuccessOutput(logId));
        } catch (Exception e) {
            context.resolve(new ErrorOutput(e.getMessage()));
        }
    }
}
