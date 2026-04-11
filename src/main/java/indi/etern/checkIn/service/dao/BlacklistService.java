package indi.etern.checkIn.service.dao;

import indi.etern.checkIn.entities.blacklist.Blacklist;
import indi.etern.checkIn.entities.blacklist.BlacklistLog;
import indi.etern.checkIn.repositories.BlacklistLogRepository;
import indi.etern.checkIn.repositories.BlacklistRepository;
import indi.etern.checkIn.utils.UUIDv7;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BlacklistService {
    public static BlacklistService singletonInstance;
    
    @Resource
    private BlacklistRepository blacklistRepository;
    
    @Resource
    private BlacklistLogRepository blacklistLogRepository;
    
    protected BlacklistService() {
        singletonInstance = this;
    }
    
    public boolean isBlacklisted(String targetId) {
        return blacklistRepository.existsByTargetId(targetId);
    }
    
    public List<Blacklist> findAll() {
        return blacklistRepository.findAll();
    }
    
    public Optional<Blacklist> findByTargetId(String targetId) {
        return blacklistRepository.findByTargetId(targetId);
    }
    
    @Transactional
    public Blacklist addToBlacklist(String targetId, String reason, Long operatorQQ) {
        Blacklist blacklist = new Blacklist();
        blacklist.setId(UUIDv7.randomUUID().toString());
        blacklist.setTargetId(targetId);
        blacklist.setReason(reason);
        blacklist.setCreatedAt(LocalDateTime.now());
        blacklist.setCreatedByQQ(operatorQQ);

        Blacklist saved = blacklistRepository.save(blacklist);

        logAction(BlacklistLog.ActionType.ADD, targetId, reason, operatorQQ);

        return saved;
    }

    @Transactional
    public void removeFromBlacklist(String targetId, Long operatorQQ) {
        Optional<Blacklist> blacklist = blacklistRepository.findByTargetId(targetId);
        String reason = blacklist.map(Blacklist::getReason).orElse(null);

        blacklistRepository.deleteByTargetId(targetId);

        logAction(BlacklistLog.ActionType.REMOVE, targetId, reason, operatorQQ);
    }

    private void logAction(BlacklistLog.ActionType action, String targetId, String reason, Long operatorQQ) {
        BlacklistLog log = new BlacklistLog();
        log.setId(UUIDv7.randomUUID().toString());
        log.setAction(action);
        log.setTargetId(targetId);
        log.setReason(reason);
        log.setOperatedByQQ(operatorQQ);
        log.setOperatedAt(LocalDateTime.now());
        blacklistLogRepository.save(log);
    }
    
    public List<BlacklistLog> getLogsByTargetId(String targetId) {
        return blacklistLogRepository.findByTargetIdOrderByOperatedAtDesc(targetId);
    }
}
