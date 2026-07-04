package indi.etern.checkIn.action.question.version;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.dto.manage.question.VersionInfoDTO;
import indi.etern.checkIn.service.question.QuestionVersionService;
import jakarta.annotation.Nonnull;

import java.util.List;

@Action("getQuestionVersionHistory")
public class GetQuestionVersionHistoryAction extends BaseAction<GetQuestionVersionHistoryAction.Input, OutputData> {
    
    private final QuestionVersionService versionService;
    
    public record Input(@Nonnull String questionId) implements InputData {}
    public record SuccessOutput(List<VersionInfoDTO> versions) implements OutputData {
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
    
    public GetQuestionVersionHistoryAction(QuestionVersionService versionService) {
        this.versionService = versionService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("questionVersion.view");
        try {
            List<VersionInfoDTO> history = versionService.getVersionHistory(context.getInput().questionId());
            context.resolve(new SuccessOutput(history));
        } catch (Exception e) {
            context.resolve(new ErrorOutput(e.getMessage()));
        }
    }
}
