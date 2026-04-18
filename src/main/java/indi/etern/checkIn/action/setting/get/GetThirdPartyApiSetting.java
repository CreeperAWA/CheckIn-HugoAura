package indi.etern.checkIn.action.setting.get;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.action.setting.save.SaveThirdPartyApiSetting;
import indi.etern.checkIn.utils.GetSettingCommon;

import java.util.LinkedHashMap;

@Action("getThirdPartyApiSetting")
public class GetThirdPartyApiSetting extends BaseAction<NullInput, GetThirdPartyApiSetting.SuccessOutput> {
    public record SuccessOutput(LinkedHashMap<String, Object> data) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }

    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("thirdPartyApi.view.setting");
        GetSettingCommon getSettingCommon = new GetSettingCommon(SaveThirdPartyApiSetting.KEYS, "thirdPartyApi");
        final LinkedHashMap<String, Object> settings = getSettingCommon.doGet();
        context.resolve(new SuccessOutput(settings));
    }
}