package indi.etern.checkIn.action.setting.get;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.action.setting.save.SaveAdvanceSetting;
import indi.etern.checkIn.entities.httpApiToken.HttpApiTokenItem;
import indi.etern.checkIn.service.dao.HttpApiTokenService;
import indi.etern.checkIn.utils.GetSettingCommon;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Action("getAdvanceSetting")
public class GetAdvanceSetting extends BaseAction<NullInput, GetAdvanceSetting.SuccessOutput> {
    public record SuccessOutput(Map<String, Object> data) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
    
    private final HttpApiTokenService httpApiTokenService;
    
    public GetAdvanceSetting(HttpApiTokenService httpApiTokenService) {
        this.httpApiTokenService = httpApiTokenService;
    }
    
    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("get advance setting");
        GetSettingCommon getSettingCommon = new GetSettingCommon(SaveAdvanceSetting.KEYS,"advance");
        final LinkedHashMap<String, Object> settings = getSettingCommon.doGet();
        final List<HttpApiTokenItem> all = httpApiTokenService.findAll();
        final List<HttpApiTokenItem> sortedList = all.stream().sorted(Comparator.comparing(HttpApiTokenItem::getGenerateTime)).toList();
        settings.put("httpApiTokenItems", sortedList);
        context.resolve(new SuccessOutput(settings));
    }
}
