package indi.etern.checkIn.action.answerLimit;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.service.dao.AnswerLimitService;

@Action("saveAnswerLimitSetting")
public class SaveAnswerLimitSettingAction extends BaseAction<SaveAnswerLimitSettingAction.Input, OutputData> {
    private final AnswerLimitService answerLimitService;
    
    public SaveAnswerLimitSettingAction(AnswerLimitService answerLimitService) {
        this.answerLimitService = answerLimitService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("answerLimit.manage.setting");
        final Input input = context.getInput();
        
        if (input.maxCount <= 0) {
            context.resolve(MessageOutput.error("最大答题次数必须大于0"));
            return;
        }
        
        answerLimitService.setMaxAnswerCount(input.maxCount);
        context.resolve(MessageOutput.success("保存成功"));
    }
    
    public record Input(int maxCount) implements InputData {
    }
}
