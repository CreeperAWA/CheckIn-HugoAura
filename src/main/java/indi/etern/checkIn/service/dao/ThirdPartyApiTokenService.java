package indi.etern.checkIn.service.dao;

import indi.etern.checkIn.entities.thirdPartyApiToken.ThirdPartyApiTokenItem;
import indi.etern.checkIn.repositories.ThirdPartyApiTokenRepository;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@CacheConfig(cacheNames = "thirdPartyApiToken")
public class ThirdPartyApiTokenService {
    @Resource
    ThirdPartyApiTokenRepository thirdPartyApiTokenRepository;
    
    @CachePut(key = "#thirdPartyApiTokenItem.id")
    public ThirdPartyApiTokenItem save(ThirdPartyApiTokenItem thirdPartyApiTokenItem) {
        return thirdPartyApiTokenRepository.save(thirdPartyApiTokenItem);
    }
    
    public List<ThirdPartyApiTokenItem> findAll() {
        return thirdPartyApiTokenRepository.findAll();
    }
    
    @CacheEvict(key = "#thirdPartyApiTokenItem.id")
    public void delete(ThirdPartyApiTokenItem thirdPartyApiTokenItem) {
        thirdPartyApiTokenRepository.delete(thirdPartyApiTokenItem);
    }
    
    @CacheEvict(allEntries = true)
    public void saveAll(List<ThirdPartyApiTokenItem> thirdPartyApiTokenItems) {
        thirdPartyApiTokenRepository.saveAll(thirdPartyApiTokenItems);
    }
    
    @CacheEvict(allEntries = true)
    public void deleteAllById(List<String> deletedThirdPartyApiTokenList) {
        thirdPartyApiTokenRepository.deleteAllById(deletedThirdPartyApiTokenList);
    }
    
    public boolean existByToken(String token) {
        return thirdPartyApiTokenRepository.existsByToken(token);
    }
    
    public boolean existBySid(String sid) {
        return thirdPartyApiTokenRepository.existsBySid(sid);
    }
}
