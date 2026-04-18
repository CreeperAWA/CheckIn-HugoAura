package indi.etern.checkIn.service.dao;

import indi.etern.checkIn.entities.httpApiToken.HttpApiTokenItem;
import indi.etern.checkIn.repositories.HttpApiTokenRepository;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@CacheConfig(cacheNames = "httpApiToken")
public class HttpApiTokenService {
    @Resource
    HttpApiTokenRepository httpApiTokenRepository;
    
    @CachePut(key = "#httpApiTokenItem.id")
    public HttpApiTokenItem save(HttpApiTokenItem httpApiTokenItem) {
        return httpApiTokenRepository.save(httpApiTokenItem);
    }
    
    public List<HttpApiTokenItem> findAll() {
        return httpApiTokenRepository.findAll();
    }
    
    @CacheEvict(key = "#httpApiTokenItem.id")
    public void delete(HttpApiTokenItem httpApiTokenItem) {
        httpApiTokenRepository.delete(httpApiTokenItem);
    }
    
    @CacheEvict(allEntries = true)
    public void saveAll(List<HttpApiTokenItem> httpApiTokenItems) {
        httpApiTokenRepository.saveAll(httpApiTokenItems);
    }
    
    @CacheEvict(allEntries = true)
    public void deleteAllById(List<String> deletedHttpApiTokenList) {
        httpApiTokenRepository.deleteAllById(deletedHttpApiTokenList);
    }
    
    public boolean existByToken(String token) {
        return httpApiTokenRepository.existsByToken(token);
    }
}