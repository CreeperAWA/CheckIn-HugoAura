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

/**
 * 获取第三方API设置
 * 
 * 返回第三方API相关的所有设置项和Token列表
 * 
 * 权限要求：thirdPartyApi.viewSetting
 * 
 * 返回数据结构：
 * - qqVerify.enabled: 是否启用QQ验证
 * - qqVerify.validDays: 验证有效期（天）
 * - qqVerify.timeoutAction: 超时处理方式
 * - qqVerify.cannotVerifyAction: 无法验证处理方式
 * - qqVerify.customStrings: 自定义验证字符串列表
 * - qqVerify.guideMessage: 验证引导提示
 * - notification.*: 各类通知设置
 * - thirdPartyApiTokenItems: 当前所有Token列表
 */
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
        context.requirePermission("thirdPartyApi.viewSetting");
        
        GetSettingCommon getSettingCommon = new GetSettingCommon(SaveThirdPartyApiSetting.KEYS, "thirdPartyApi");
        final LinkedHashMap<String, Object> settings = getSettingCommon.doGet();
        
        final List<ThirdPartyApiTokenItem> all = thirdPartyApiTokenService.findAll();
        final List<ThirdPartyApiTokenItem> sortedList = all.stream()
            .sorted(Comparator.comparing(ThirdPartyApiTokenItem::getGenerateTime))
            .toList();
        settings.put("thirdPartyApiTokenItems", sortedList);
        
        context.resolve(new SuccessOutput(settings));
    }
}
