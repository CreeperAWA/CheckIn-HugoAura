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

@Action("saveThirdPartyApiSetting")
public class SaveThirdPartyApiSetting extends BaseAction<SaveThirdPartyApiSetting.Input, MessageOutput> {
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

    @Transactional
    @Override
    public void execute(ExecuteContext<Input, MessageOutput> context) {
        context.requirePermission("thirdPartyApi.manage.setting");
        SaveSettingCommon saveSettingCommon = new SaveSettingCommon(context.getInput().data,
                KEYS, "thirdPartyApi");
        try {
            saveSettingCommon.doSave();
            context.resolve(MessageOutput.success("设置保存成功"));
        } catch (Exception e) {
            context.resolve(MessageOutput.error("设置保存失败: " + e.getMessage()));
        }
    }
}