package indi.etern.checkIn.action.setting.save;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.auth.JwtTokenProvider;
import indi.etern.checkIn.entities.thirdPartyApiToken.ThirdPartyApiTokenItem;
import indi.etern.checkIn.service.dao.ThirdPartyApiTokenService;
import indi.etern.checkIn.utils.SaveSettingCommon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 保存第三方API设置
 * 
 * 保存第三方API相关的所有设置项，并处理Token的创建和删除
 * 
 * 权限要求：thirdPartyApi.manageSetting
 * 
 * 输入数据结构：
 * - data: 包含所有设置项的Map
 *   - qqVerify.*: QQ验证相关设置
 *   - notification.*: 通知相关设置
 *   - createdThirdPartyApiTokens: 要创建的新Token列表
 *   - deletedThirdPartyApiTokenIds: 要删除的Token ID列表
 * 
 * 输出数据：
 * - currentTokens: 更新后的Token列表（仅在创建或删除Token时返回）
 */
@Action("saveThirdPartyApiSetting")
public class SaveThirdPartyApiSetting extends BaseAction<SaveThirdPartyApiSetting.Input, SaveThirdPartyApiSetting.ThirdPartyApiTokenOutput> {
    
    private static final Logger logger = LoggerFactory.getLogger(SaveThirdPartyApiSetting.class);
    
    public record Input(Map<String, Object> data) implements InputData {}
    
    public record ThirdPartyApiTokenOutput(List<ThirdPartyApiTokenItem> currentTokens) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }

    /**
     * 可配置的设置项键名列表
     * 
     * 这些键名会与根名称（"thirdPartyApi"）组合形成完整的设置键
     */
    public static final String[] KEYS = {
            "qqVerify.enabled",
            "qqVerify.validDays",
            "qqVerify.timeoutAction",
            "qqVerify.cannotVerifyAction",
            "qqVerify.customStrings",
            "qqVerify.guideMessage",
            "qqVerify.whitelist",
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
    
    private SaveSettingCommon saveSettingCommon;

    private final ThirdPartyApiTokenService thirdPartyApiTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    public SaveThirdPartyApiSetting(ThirdPartyApiTokenService thirdPartyApiTokenService, JwtTokenProvider jwtTokenProvider) {
        this.thirdPartyApiTokenService = thirdPartyApiTokenService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    @Override
    public void execute(ExecuteContext<Input, ThirdPartyApiTokenOutput> context) {
        final Input input = context.getInput();
        context.requirePermission("thirdPartyApi.manageSetting");
        
        saveSettingCommon = new SaveSettingCommon(input.data, KEYS, "thirdPartyApi");
        
        //noinspection unchecked
        List<Map<String, String>> createdThirdPartyApiTokenList = 
            (List<Map<String, String>>) input.data.get("createdThirdPartyApiTokens");
        //noinspection unchecked
        List<String> deletedThirdPartyApiTokenList = 
            (List<String>) input.data.get("deletedThirdPartyApiTokenIds");

        saveSettingCommon.doSave();
        
        boolean tokensChanged = false;
        
        if (createdThirdPartyApiTokenList != null && !createdThirdPartyApiTokenList.isEmpty()) {
            logger.info("正在创建 {} 个新的 ThirdPartyApiToken", createdThirdPartyApiTokenList.size());
            List<ThirdPartyApiTokenItem> thirdPartyApiTokenItems = new ArrayList<>();
            for (Map<String, String> tokenData : createdThirdPartyApiTokenList) {
                thirdPartyApiTokenItems.add(ThirdPartyApiTokenItem.generateNewToken(
                    tokenData.get("id"), 
                    tokenData.get("sid"), 
                    tokenData.get("description"), 
                    context.getCurrentUser(),
                    jwtTokenProvider
                ));
            }
            thirdPartyApiTokenService.saveAll(thirdPartyApiTokenItems);
            tokensChanged = true;
        }
        
        if (deletedThirdPartyApiTokenList != null && !deletedThirdPartyApiTokenList.isEmpty()) {
            logger.info("Deleting {} ThirdPartyApiTokens", deletedThirdPartyApiTokenList.size());
            thirdPartyApiTokenService.deleteAllById(deletedThirdPartyApiTokenList);
            tokensChanged = true;
        }
        
        ThirdPartyApiTokenOutput thirdPartyApiTokenOutput;
        if (tokensChanged) {
            final List<ThirdPartyApiTokenItem> all = thirdPartyApiTokenService.findAll();
            final List<ThirdPartyApiTokenItem> sortedList = all.stream()
                .sorted(Comparator.comparing(ThirdPartyApiTokenItem::getGenerateTime))
                .toList();
            thirdPartyApiTokenOutput = new ThirdPartyApiTokenOutput(sortedList);
        } else {
            thirdPartyApiTokenOutput = new ThirdPartyApiTokenOutput(null);
        }

        context.resolve(thirdPartyApiTokenOutput);
    }
}
