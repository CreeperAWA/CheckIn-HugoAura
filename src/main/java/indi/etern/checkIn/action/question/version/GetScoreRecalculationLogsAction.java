package indi.etern.checkIn.action.question.version;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.dto.manage.question.RecalculationLogDTO;
import indi.etern.checkIn.entities.question.version.ScoreRecalculationLog;
import indi.etern.checkIn.repositories.ScoreChangeDetailRepository;
import indi.etern.checkIn.repositories.ScoreRecalculationLogRepository;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@Action("getScoreRecalculationLogs")
public class GetScoreRecalculationLogsAction extends BaseAction<GetScoreRecalculationLogsAction.Input, OutputData> {
    
    private final ScoreRecalculationLogRepository recalcLogRepository;
    private final ScoreChangeDetailRepository changeDetailRepository;
    
    public record Input(@Nullable String questionId, int page, int size) implements InputData {}
    public record SuccessOutput(List<RecalculationLogDTO> logs, int totalPages, long totalElements) implements OutputData {
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
    
    public GetScoreRecalculationLogsAction(ScoreRecalculationLogRepository recalcLogRepository,
                                           ScoreChangeDetailRepository changeDetailRepository) {
        this.recalcLogRepository = recalcLogRepository;
        this.changeDetailRepository = changeDetailRepository;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("questionVersion.viewRecalculationLog");
        try {
            Input input = context.getInput();
            Page<ScoreRecalculationLog> page;
            if (input.questionId() != null && !input.questionId().isBlank()) {
                List<ScoreRecalculationLog> logs = recalcLogRepository.findByQuestionIdOrderByTriggeredAtDesc(input.questionId());
                List<RecalculationLogDTO> dtos = logs.stream().map(log -> RecalculationLogDTO.from(log, null)).toList();
                context.resolve(new SuccessOutput(dtos, 1, logs.size()));
            } else {
                page = recalcLogRepository.findAllByOrderByTriggeredAtDesc(PageRequest.of(input.page(), input.size()));
                List<RecalculationLogDTO> dtos = page.getContent().stream()
                        .map(log -> RecalculationLogDTO.from(log, null))
                        .toList();
                context.resolve(new SuccessOutput(dtos, page.getTotalPages(), page.getTotalElements()));
            }
        } catch (Exception e) {
            context.resolve(new ErrorOutput(e.getMessage()));
        }
    }
}
