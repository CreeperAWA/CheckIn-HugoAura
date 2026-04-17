package indi.etern.checkIn.service.dao;

import indi.etern.checkIn.entities.whitelist.Whitelist;
import indi.etern.checkIn.repositories.WhitelistRepository;
import indi.etern.checkIn.utils.UUIDv7;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WhitelistService {
    public static WhitelistService singletonInstance;
    
    @Resource
    private WhitelistRepository whitelistRepository;
    
    protected WhitelistService() {
        singletonInstance = this;
    }
    
    public boolean isWhitelisted(String targetId) {
        return whitelistRepository.existsByTargetId(targetId);
    }
    
    public List<Whitelist> findAll() {
        return whitelistRepository.findAll();
    }
    
    public Optional<Whitelist> findByTargetId(String targetId) {
        return whitelistRepository.findByTargetId(targetId);
    }
    
    @Transactional
    public Whitelist addToWhitelist(String targetId, String reason, Long operatorQQ) {
        Whitelist whitelist = new Whitelist();
        whitelist.setId(UUIDv7.randomUUID().toString());
        whitelist.setTargetId(targetId);
        whitelist.setReason(reason);
        whitelist.setCreatedAt(LocalDateTime.now());
        whitelist.setCreatedByQQ(operatorQQ);

        return whitelistRepository.save(whitelist);
    }

    @Transactional
    public void removeFromWhitelist(String targetId, Long operatorQQ) {
        whitelistRepository.deleteByTargetId(targetId);
    }
}
