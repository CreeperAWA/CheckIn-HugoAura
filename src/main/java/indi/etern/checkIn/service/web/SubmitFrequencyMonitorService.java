package indi.etern.checkIn.service.web;

import indi.etern.checkIn.entities.setting.SettingItem;
import indi.etern.checkIn.service.dao.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubmitFrequencyMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(SubmitFrequencyMonitorService.class);
    public static volatile SubmitFrequencyMonitorService singletonInstance;
    
    private final SettingService settingService;
    private final ThirdPartyApiWebSocketService thirdPartyApiWebSocketService;
    
    // 存储用户的提交记录：键为QQ号，值为提交时间列表
    private final Map<Long, Map<String, LocalDateTime>> userSubmitRecords = new ConcurrentHashMap<>();
    
    public SubmitFrequencyMonitorService(SettingService settingService, ThirdPartyApiWebSocketService thirdPartyApiWebSocketService) {
        this.settingService = settingService;
        this.thirdPartyApiWebSocketService = thirdPartyApiWebSocketService;
        singletonInstance = this;
    }
    
    /**
     * 记录用户提交并检查是否需要发送通知
     * @param qq 用户QQ号
     * @param examId 考试ID
     */
    public void recordSubmit(long qq, String examId) {
        try {
            // 检查是否启用了提交频率监控
            SettingItem submitFrequencyEnabled = settingService.getItem("thirdPartyApi.notification", "submitFrequency.enabled");
            if (!submitFrequencyEnabled.getValue(Boolean.class)) {
                return;
            }
            
            // 获取监控配置
            SettingItem timeWindowSetting = settingService.getItem("thirdPartyApi.notification", "submitFrequency.timeWindow");
            SettingItem thresholdSetting = settingService.getItem("thirdPartyApi.notification", "submitFrequency.threshold");
            
            int timeWindowMinutes = timeWindowSetting.getValue(Integer.class);
            int threshold = thresholdSetting.getValue(Integer.class);
            
            // 记录提交时间
            LocalDateTime now = LocalDateTime.now();
            Map<String, LocalDateTime> examSubmits = userSubmitRecords.computeIfAbsent(qq, k -> new HashMap<>());
            examSubmits.put(examId, now);
            
            // 清理过期的记录
            examSubmits.entrySet().removeIf(entry -> java.time.Duration.between(entry.getValue(), now).toMinutes() > timeWindowMinutes);
            
            // 检查是否达到阈值
            if (examSubmits.size() >= threshold) {
                // 发送通知
                Map<String, Object> data = new HashMap<>();
                data.put("qq", String.valueOf(qq));
                data.put("time_window", timeWindowMinutes);
                data.put("submit_count", examSubmits.size());
                
                // 计算时间范围
                LocalDateTime earliestTime = examSubmits.values().stream().min(LocalDateTime::compareTo).orElse(now);
                data.put("start_time", new int[]{earliestTime.getYear(), earliestTime.getMonthValue(), earliestTime.getDayOfMonth(), earliestTime.getHour(), earliestTime.getMinute(), earliestTime.getSecond(), earliestTime.getNano()});
                data.put("end_time", new int[]{now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano()});
                
                thirdPartyApiWebSocketService.sendNotification("notification_submit_frequency", data);
                
                logger.debug("Sent submit frequency notification for user {}: {} submits in {} minutes", qq, examSubmits.size(), timeWindowMinutes);
            }
        } catch (Exception e) {
            logger.debug("Failed to monitor submit frequency: {}", e.getMessage());
        }
    }
}