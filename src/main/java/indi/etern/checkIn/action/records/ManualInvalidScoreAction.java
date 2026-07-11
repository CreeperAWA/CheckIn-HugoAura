package indi.etern.checkIn.action.records;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.entities.exam.ExamData;
import indi.etern.checkIn.service.dao.ExamDataService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Action("InvalidScore")
public class ManualInvalidScoreAction extends BaseAction<ManualInvalidScoreAction.Input, MessageOutput> {
    public record Input(String id) implements InputData {}
    
    private final ExamDataService examDataService;
    
    public ManualInvalidScoreAction(ExamDataService examDataService) {
        this.examDataService = examDataService;
    }
    
    @Override
    @Transactional
    public void execute(ExecuteContext<Input, MessageOutput> context) {
        context.requirePermission("examData.manualInvalidScore");
        Optional<ExamData> optionalExamData = examDataService.findById(context.getInput().id);
        optionalExamData.ifPresentOrElse((examData) -> {
            if (examData.getStatus() != ExamData.Status.SUBMITTED) {
                context.resolve(MessageOutput.error("Exam is not submitted yet"));
                return;
            }
            examData.setStatus(ExamData.Status.SCORE_INVALIDED);
            examDataService.save(examData);
            examData.sendUpdateExamRecord();
            context.resolve(MessageOutput.success("Score invalidated"));
        },() -> context.resolve(MessageOutput.error("Exam data not found")));
    }
}
