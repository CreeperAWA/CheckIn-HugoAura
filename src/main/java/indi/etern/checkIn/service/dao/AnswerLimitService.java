package indi.etern.checkIn.service.dao;

import indi.etern.checkIn.entities.answerLimit.AnswerLimitWhitelist;
import indi.etern.checkIn.entities.exam.ExamData;
import indi.etern.checkIn.entities.setting.SettingItem;
import indi.etern.checkIn.repositories.AnswerLimitWhitelistRepository;
import indi.etern.checkIn.repositories.ExamDataRepository;
import indi.etern.checkIn.utils.UUIDv7;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AnswerLimitService {
    
    @Resource
    private AnswerLimitWhitelistRepository answerLimitWhitelistRepository;
    
    @Resource
    private ExamDataRepository examDataRepository;
    
    @Resource
    private SettingService settingService;
    
    public AnswerLimitService() {
    }
    
    public boolean isInWhitelist(String qq) {
        return answerLimitWhitelistRepository.existsByQq(qq);
    }
    
    public boolean hasExceededLimit(String qq) {
        if (isInWhitelist(qq)) {
            return false;
        }
        
        int maxCount = getMaxAnswerCount();
        int currentCount = getAnswerCount(qq);
        
        return currentCount >= maxCount;
    }
    
    public int getMaxAnswerCount() {
        try {
            SettingItem item = settingService.getItem("answerLimit", "maxCount");
            return item.getValue(Integer.class);
        } catch (Exception e) {
            return 5;
        }
    }
    
    public void setMaxAnswerCount(int maxCount) {
        SettingItem item = new SettingItem("answerLimit.maxCount", maxCount);
        settingService.setSetting(item);
    }
    
    public int getAnswerCount(String qq) {
        try {
            long qqNumber = Long.parseLong(qq);
            List<ExamData> examDataList = examDataRepository.findAllByQqNumberIs(qqNumber);
            return (int) examDataList.stream()
                    .filter(examData -> examData.getExamResult() != null)
                    .count();
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    public LocalDateTime getFirstAnswerTime(String qq) {
        try {
            long qqNumber = Long.parseLong(qq);
            List<ExamData> examDataList = examDataRepository.findAllByQqNumberIs(qqNumber);
            return examDataList.stream()
                    .filter(examData -> examData.getExamResult() != null)
                    .map(ExamData::getSubmitTime)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public LocalDateTime getLastAnswerTime(String qq) {
        try {
            long qqNumber = Long.parseLong(qq);
            List<ExamData> examDataList = examDataRepository.findAllByQqNumberIs(qqNumber);
            return examDataList.stream()
                    .filter(examData -> examData.getExamResult() != null)
                    .map(ExamData::getSubmitTime)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public List<ExamData> getExamRecordsByQQ(String qq) {
        try {
            long qqNumber = Long.parseLong(qq);
            return examDataRepository.findAllByQqNumberIs(qqNumber);
        } catch (NumberFormatException e) {
            return List.of();
        }
    }
    
    @Transactional
    public void deleteExamRecord(String examId) {
        examDataRepository.deleteById(examId);
    }
    
    public List<AnswerLimitWhitelist> getAllWhitelist() {
        return answerLimitWhitelistRepository.findAll();
    }
    
    public Optional<AnswerLimitWhitelist> findWhitelistByQq(String qq) {
        return answerLimitWhitelistRepository.findByQq(qq);
    }
    
    @Transactional
    public AnswerLimitWhitelist addToWhitelist(String qq, String reason, Long operatorQQ) {
        AnswerLimitWhitelist whitelist = new AnswerLimitWhitelist();
        whitelist.setId(UUIDv7.randomUUID().toString());
        whitelist.setQq(qq);
        whitelist.setReason(reason);
        whitelist.setCreatedAt(LocalDateTime.now());
        whitelist.setCreatedByQQ(operatorQQ);
        
        return answerLimitWhitelistRepository.save(whitelist);
    }
    
    @Transactional
    public void removeFromWhitelist(String qq) {
        answerLimitWhitelistRepository.deleteByQq(qq);
    }
}
