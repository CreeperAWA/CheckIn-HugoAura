package indi.etern.checkIn.action.question.version;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.service.question.QuestionVersionService;
import jakarta.annotation.Nonnull;

import java.util.Optional;

@Action(value = "getActiveVersionByGroupId", exposed = true)
public class GetActiveVersionByGroupIdAction extends BaseAction<GetActiveVersionByGroupIdAction.Input, OutputData> {
    
    private final QuestionVersionService versionService;
    
    public record Input(@Nonnull String versionGroupId) implements InputData {}
    public record SuccessOutput(String questionId) implements OutputData {
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
    
    public GetActiveVersionByGroupIdAction(QuestionVersionService versionService) {
        this.versionService = versionService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        String versionGroupId = context.getInput().versionGroupId();
        Optional<Question> activeVersion = versionService.findActiveVersionByVersionGroupId(versionGroupId);
        if (activeVersion.isPresent()) {
            context.resolve(new SuccessOutput(activeVersion.get().getId()));
        } else {
            context.resolve(new ErrorOutput("No active version found for group: " + versionGroupId));
        }
    }
}
