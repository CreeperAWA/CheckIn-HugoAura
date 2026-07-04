package indi.etern.checkIn.action.question.version;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.dto.manage.question.PendingRecalculationDTO;
import indi.etern.checkIn.entities.question.version.ScoreRecalculationLog;
import indi.etern.checkIn.service.question.ScoreRecalculationService;

import java.util.List;

@Action("getPendingRecalculations")
public class GetPendingRecalculationsAction extends BaseAction<GetPendingRecalculationsAction.Input, OutputData> {
    
    private final ScoreRecalculationService recalculationService;
    
    public record Input() implements InputData {}
    public record SuccessOutput(List<PendingRecalculationDTO> recalculations) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
    
    public GetPendingRecalculationsAction(ScoreRecalculationService recalculationService) {
        this.recalculationService = recalculationService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("questionVersion.approveRecalculation");
        List<ScoreRecalculationLog> logs = recalculationService.getPendingRecalculations();
        List<PendingRecalculationDTO> dtos = logs.stream()
                .map(log -> PendingRecalculationDTO.from(log, recalculationService.getAffectedExamIds(log.getQuestionId())))
                .toList();
        context.resolve(new SuccessOutput(dtos));
    }
}
