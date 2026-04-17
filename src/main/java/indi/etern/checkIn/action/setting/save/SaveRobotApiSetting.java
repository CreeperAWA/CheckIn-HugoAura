package indi.etern.checkIn.action.setting.save;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.MessageOutput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.utils.SaveSettingCommon;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Action("saveRobotApiSetting")
public class SaveRobotApiSetting extends BaseAction<SaveRobotApiSetting.Input, OutputData> {
    public record Input(Map<String, Object> data) implements InputData {}
    
    public static final String[] KEYS = {
            "qqVerify.enabled",
            "qqVerify.validDays",
            "qqVerify.timeoutAction",
            "qqVerify.cannotVerifyAction",
            "qqVerify.customStrings",
            "notification.submitFrequency.enabled",
            "notification.submitFrequency.timeWindow",
            "notification.submitFrequency.threshold",
            "notification.loginFailure.enabled",
            "notification.loginFailure.threshold",
            "notification.loginSuccess.enabled",
            "notification.quickSubmit.enabled",
            "notification.quickSubmit.threshold",
            "notification.paperSubmit.enabled",
            "notification.examStart.enabled"
    };
    SaveSettingCommon saveSettingCommon;
    
    @Transactional
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        context.requirePermission("robotApi.manage.setting");
        final Input input = context.getInput();
        saveSettingCommon = new SaveSettingCommon(input.data,
                KEYS, "robotApi");
        try {
            saveSettingCommon.doSave();
            context.resolve(MessageOutput.success("设置保存成功"));
        } catch (Exception e) {
            context.resolve(MessageOutput.error("设置保存失败: " + e.getMessage()));
        }
    }
}
