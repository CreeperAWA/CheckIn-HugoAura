package indi.etern.checkIn.action.setting.save;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.httpApiToken.HttpApiTokenItem;
import indi.etern.checkIn.service.dao.HttpApiTokenService;
import indi.etern.checkIn.utils.SaveSettingCommon;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Action("saveAdvanceSetting")
public class SaveAdvanceSetting extends BaseAction<SaveAdvanceSetting.Input, SaveAdvanceSetting.HttpApiTokenOutput> {
    public record Input(Map<String, Object> data) implements InputData {}
    public record HttpApiTokenOutput(List<HttpApiTokenItem> currentTokens) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
    
    public static final String[] KEYS = {
            "ipSource",
            "ipSourceCustomHeaderRegex",
            "useRequestIpIfSourceIsNull",
            "autoCreateUserMode",
            "enableTurnstileOnLogin",
            "enableTurnstileOnExam",
            "turnstileSiteKey",
            "turnstileSecret"
    };
    SaveSettingCommon saveSettingCommon;
    
    private final HttpApiTokenService httpApiTokenService;
    
    public SaveAdvanceSetting(HttpApiTokenService httpApiTokenService) {
        this.httpApiTokenService = httpApiTokenService;
    }
    
    @Transactional
    @Override
    public void execute(ExecuteContext<Input, HttpApiTokenOutput> context) {
        final Input input = context.getInput();
        saveSettingCommon = new SaveSettingCommon(input.data,
                KEYS, "advance");
        //noinspection unchecked
        List<Map<String,String>> createdRobotTokenList = (List<Map<String, String>>) input.data.get("createdHttpApiTokens");
        //noinspection unchecked
        List<String> deletedRobotTokenList = (List<String>) input.data.get("deletedHttpApiTokenIds");
        context.requirePermission("setting.saveAdvance");
        saveSettingCommon = new SaveSettingCommon(input.data,
                KEYS, "advance");
        
        saveSettingCommon.doSave();
        if (createdRobotTokenList != null) {
            List<HttpApiTokenItem> httpApiTokenItems = new ArrayList<>();
            for (Map<String, String> tokenData : createdRobotTokenList) {
                httpApiTokenItems.add(HttpApiTokenItem.generateNewToken(tokenData.get("id"), tokenData.get("description"), context.getCurrentUser()));
            }
            httpApiTokenService.saveAll(httpApiTokenItems);
        }
        if (deletedRobotTokenList != null) {
            httpApiTokenService.deleteAllById(deletedRobotTokenList);
        }
        HttpApiTokenOutput httpApiTokenOutput;
        if (createdRobotTokenList != null || deletedRobotTokenList != null) {
            final List<HttpApiTokenItem> all = httpApiTokenService.findAll();
            final List<HttpApiTokenItem> sortedList = all.stream().sorted(Comparator.comparing(HttpApiTokenItem::getGenerateTime)).toList();
            httpApiTokenOutput = new HttpApiTokenOutput(sortedList);
        } else {
            httpApiTokenOutput = new HttpApiTokenOutput(null);
        }
        
        context.resolve(httpApiTokenOutput);
    }
}
