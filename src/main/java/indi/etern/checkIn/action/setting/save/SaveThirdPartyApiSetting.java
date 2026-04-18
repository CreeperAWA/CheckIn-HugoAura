package indi.etern.checkIn.action.setting.save;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.thirdPartyApiToken.ThirdPartyApiTokenItem;
import indi.etern.checkIn.service.dao.ThirdPartyApiTokenService;
import indi.etern.checkIn.utils.SaveSettingCommon;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Action("saveThirdPartyApiSetting")
public class SaveThirdPartyApiSetting extends BaseAction<SaveThirdPartyApiSetting.Input, SaveThirdPartyApiSetting.ThirdPartyApiTokenOutput> {
    public record Input(Map<String, Object> data) implements InputData {}
    public record ThirdPartyApiTokenOutput(List<ThirdPartyApiTokenItem> currentTokens) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }

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
            "notification.loginSuccess.enabled",
            "notification.quickSubmit.enabled",
            "notification.quickSubmit.threshold",
            "notification.paperSubmit.enabled",
            "notification.examStart.enabled"
    };
    SaveSettingCommon saveSettingCommon;

    private final ThirdPartyApiTokenService thirdPartyApiTokenService;

    public SaveThirdPartyApiSetting(ThirdPartyApiTokenService thirdPartyApiTokenService) {
        this.thirdPartyApiTokenService = thirdPartyApiTokenService;
    }

    @Transactional
    @Override
    public void execute(ExecuteContext<Input, ThirdPartyApiTokenOutput> context) {
        final Input input = context.getInput();
        saveSettingCommon = new SaveSettingCommon(input.data,
                KEYS, "thirdPartyApi");
        //noinspection unchecked
        List<Map<String, String>> createdThirdPartyApiTokenList = (List<Map<String, String>>) input.data.get("createdThirdPartyApiTokens");
        //noinspection unchecked
        List<String> deletedThirdPartyApiTokenList = (List<String>) input.data.get("deletedThirdPartyApiTokenIds");
        context.requirePermission("thirdPartyApi.manage.setting");
        saveSettingCommon = new SaveSettingCommon(input.data,
                KEYS, "thirdPartyApi");

        saveSettingCommon.doSave();
        if (createdThirdPartyApiTokenList != null) {
            List<ThirdPartyApiTokenItem> thirdPartyApiTokenItems = new ArrayList<>();
            for (Map<String, String> tokenData : createdThirdPartyApiTokenList) {
                thirdPartyApiTokenItems.add(ThirdPartyApiTokenItem.generateNewToken(tokenData.get("id"), tokenData.get("sid"), tokenData.get("description"), context.getCurrentUser()));
            }
            thirdPartyApiTokenService.saveAll(thirdPartyApiTokenItems);
        }
        if (deletedThirdPartyApiTokenList != null) {
            thirdPartyApiTokenService.deleteAllById(deletedThirdPartyApiTokenList);
        }
        ThirdPartyApiTokenOutput thirdPartyApiTokenOutput;
        if (createdThirdPartyApiTokenList != null || deletedThirdPartyApiTokenList != null) {
            final List<ThirdPartyApiTokenItem> all = thirdPartyApiTokenService.findAll();
            final List<ThirdPartyApiTokenItem> sortedList = all.stream().sorted(Comparator.comparing(ThirdPartyApiTokenItem::getGenerateTime)).toList();
            thirdPartyApiTokenOutput = new ThirdPartyApiTokenOutput(sortedList);
        } else {
            thirdPartyApiTokenOutput = new ThirdPartyApiTokenOutput(null);
        }

        context.resolve(thirdPartyApiTokenOutput);
    }
}