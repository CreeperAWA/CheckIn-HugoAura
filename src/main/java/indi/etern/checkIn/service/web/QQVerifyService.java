package indi.etern.checkIn.service.web;

import indi.etern.checkIn.entities.setting.SettingItem;
import indi.etern.checkIn.service.dao.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QQ验证服务
 * 
 * 负责管理与QQ验证相关的业务逻辑，包括：
 * 1. 验证功能启用检查
 * 2. 白名单检查
 * 3. 验证有效期管理
 * 4. 验证状态缓存
 */
@Service
public class QQVerifyService {
    
    private static final Logger logger = LoggerFactory.getLogger(QQVerifyService.class);
    
    private final SettingService settingService;
    
    /**
     * 已验证的QQ号缓存（内存缓存，JVM重启后清空）
     * key: QQ号, value: 验证通过的时间戳
     * 验证成功后，该QQ号在配置的有效期内可免验证生成题目
     */
    private final ConcurrentHashMap<String, Long> verifiedQQCache = new ConcurrentHashMap<>();
    
    public QQVerifyService(SettingService settingService) {
        this.settingService = settingService;
    }
    
    /**
     * 检查QQ验证功能是否启用
     * @return true表示启用，false表示未启用
     */
    public boolean isVerifyEnabled() {
        try {
            SettingItem qqVerifyEnabled = settingService.getItem("thirdPartyApi.qqVerify", "enabled");
            return Boolean.TRUE.equals(qqVerifyEnabled.getValue(Boolean.class));
        } catch (Exception e) {
            logger.warn("Failed to get qqVerifyEnabled setting, defaulting to false", e);
            return false;
        }
    }
    
    /**
     * 检查QQ号是否在白名单中
     * @param qqStr QQ号字符串
     * @return true表示在白名单中，false表示不在
     */
    public boolean isInWhitelist(String qqStr) {
        try {
            SettingItem whitelistSetting = settingService.getItem("thirdPartyApi.qqVerify", "whitelist");
            List<?> whitelistRaw = whitelistSetting.getValue(List.class);
            if (whitelistRaw != null && !whitelistRaw.isEmpty()) {
                for (Object item : whitelistRaw) {
                    if (qqStr.equals(String.valueOf(item).trim())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to check whitelist for QQ: {}", qqStr, e);
        }
        return false;
    }
    
    /**
     * 获取验证有效期天数
     * @return 有效期天数，默认1天
     */
    public int getValidDays() {
        try {
            SettingItem validDaysSetting = settingService.getItem("thirdPartyApi.qqVerify", "validDays");
            int validDays = validDaysSetting.getValue(Integer.class);
            if (validDays <= 0) {
                return 1;
            }
            return validDays;
        } catch (Exception e) {
            logger.warn("Failed to get validDays setting, using default 1 day", e);
            return 1;
        }
    }
    
    /**
     * 检查QQ号是否在验证有效期内
     * @param qqStr QQ号字符串
     * @return true表示在有效期内，false表示不在或已过期
     */
    public boolean isVerifiedWithinValidPeriod(String qqStr) {
        try {
            Long verifiedTime = verifiedQQCache.get(qqStr);
            if (verifiedTime != null) {
                int validDays = getValidDays();
                long validDurationMs = validDays * 24L * 60L * 60L * 1000L;
                return (System.currentTimeMillis() - verifiedTime) < validDurationMs;
            }
        } catch (Exception e) {
            logger.warn("Failed to check verified cache for QQ: {}", qqStr, e);
        }
        return false;
    }
    
    /**
     * 将QQ号添加到已验证缓存
     * @param qqStr QQ号字符串
     */
    public void addToVerifiedCache(String qqStr) {
        verifiedQQCache.put(qqStr, System.currentTimeMillis());
        logger.info("QQ {} added to verified cache", qqStr);
    }
    
    /**
     * 从已验证缓存中移除过期的QQ号
     * @param qqStr QQ号字符串
     */
    public void removeFromVerifiedCache(String qqStr) {
        verifiedQQCache.remove(qqStr);
        logger.info("QQ {} removed from verified cache", qqStr);
    }
    
    /**
     * 综合检查：判断QQ号是否需要验证
     * @param qqStr QQ号字符串
     * @return true表示需要验证，false表示不需要（验证未启用、在白名单中或已验证且在有效期内）
     */
    public boolean needsVerification(String qqStr) {
        if (!isVerifyEnabled()) {
            return false;
        }
        
        if (isInWhitelist(qqStr)) {
            return false;
        }
        
        return !isVerifiedWithinValidPeriod(qqStr);
    }
}
