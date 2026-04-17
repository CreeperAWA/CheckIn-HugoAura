package indi.etern.checkIn.action.setting.get;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.action.setting.save.SaveRobotApiSetting;
import indi.etern.checkIn.utils.GetSettingCommon;

import java.util.LinkedHashMap;

@Action("getRobotApiSetting")
public class GetRobotApiSetting extends BaseAction<NullInput, GetRobotApiSetting.SuccessOutput> {
    public record SuccessOutput(LinkedHashMap<String, Object> data) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }

    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("robotApi.view.setting");
        GetSettingCommon getSettingCommon = new GetSettingCommon(SaveRobotApiSetting.KEYS, "robotApi");
        final LinkedHashMap<String, Object> settings = getSettingCommon.doGet();
        context.resolve(new SuccessOutput(settings));
    }
}
