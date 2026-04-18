package indi.etern.checkIn.action.setting.get;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.action.setting.save.SaveThirdPartyApiSetting;
import indi.etern.checkIn.entities.thirdPartyApiToken.ThirdPartyApiTokenItem;
import indi.etern.checkIn.service.dao.ThirdPartyApiTokenService;
import indi.etern.checkIn.utils.GetSettingCommon;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

@Action("getThirdPartyApiSetting")
public class GetThirdPartyApiSetting extends BaseAction<NullInput, GetThirdPartyApiSetting.SuccessOutput> {
    public record SuccessOutput(LinkedHashMap<String, Object> data) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
    
    private final ThirdPartyApiTokenService thirdPartyApiTokenService;
    
    public GetThirdPartyApiSetting(ThirdPartyApiTokenService thirdPartyApiTokenService) {
        this.thirdPartyApiTokenService = thirdPartyApiTokenService;
    }

    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("thirdPartyApi.view.setting");
        GetSettingCommon getSettingCommon = new GetSettingCommon(SaveThirdPartyApiSetting.KEYS, "thirdPartyApi");
        final LinkedHashMap<String, Object> settings = getSettingCommon.doGet();
        final List<ThirdPartyApiTokenItem> all = thirdPartyApiTokenService.findAll();
        final List<ThirdPartyApiTokenItem> sortedList = all.stream().sorted(Comparator.comparing(ThirdPartyApiTokenItem::getGenerateTime)).toList();
        settings.put("thirdPartyApiTokenItems", sortedList);
        context.resolve(new SuccessOutput(settings));
    }
}