package indi.etern.checkIn.service.dao;

import indi.etern.checkIn.entities.thirdPartyApiToken.ThirdPartyApiTokenItem;
import indi.etern.checkIn.repositories.ThirdPartyApiTokenRepository;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 第三方API Token服务
 * 
 * 负责第三方API Token的数据访问和业务逻辑处理
 * 主要职责：
 * 1. Token的保存和查询
 * 2. Token的删除和批量操作
 * 3. Token存在性检查
 * 4. 缓存管理
 * 
 * 缓存策略：
 * - 使用"thirdPartyApiToken"作为缓存名称
 * - save操作使用@CachePut更新缓存
 * - delete操作使用@CacheEvict清除缓存
 * - 批量操作使用@CacheEvict(allEntries = true)清除所有缓存
 */
@Service
@CacheConfig(cacheNames = "thirdPartyApiToken")
public class ThirdPartyApiTokenService {
    
    private static final Logger logger = LoggerFactory.getLogger(ThirdPartyApiTokenService.class);
    
    @Resource
    ThirdPartyApiTokenRepository thirdPartyApiTokenRepository;
    
    /**
     * 保存单个Token
     * 
     * @param thirdPartyApiTokenItem 要保存的Token项
     * @return 保存后的Token项
     */
    @CachePut(key = "#thirdPartyApiTokenItem.id")
    public ThirdPartyApiTokenItem save(ThirdPartyApiTokenItem thirdPartyApiTokenItem) {
        logger.info("Saving ThirdPartyApiToken, id: {}, sid: {}", 
            thirdPartyApiTokenItem.getId(), thirdPartyApiTokenItem.getSid());
        return thirdPartyApiTokenRepository.save(thirdPartyApiTokenItem);
    }
    
    /**
     * 查询所有Token
     * 
     * @return 所有Token项列表
     */
    public List<ThirdPartyApiTokenItem> findAll() {
        List<ThirdPartyApiTokenItem> tokens = thirdPartyApiTokenRepository.findAll();
        logger.debug("Found {} ThirdPartyApiTokens", tokens.size());
        return tokens;
    }
    
    /**
     * 删除单个Token
     * 
     * @param thirdPartyApiTokenItem 要删除的Token项
     */
    @CacheEvict(key = "#thirdPartyApiTokenItem.id")
    public void delete(ThirdPartyApiTokenItem thirdPartyApiTokenItem) {
        logger.info("Deleting ThirdPartyApiToken, id: {}", thirdPartyApiTokenItem.getId());
        thirdPartyApiTokenRepository.delete(thirdPartyApiTokenItem);
    }
    
    /**
     * 批量保存Token
     * 
     * 注意：此操作会清除所有缓存
     * 
     * @param thirdPartyApiTokenItems 要保存的Token项列表
     */
    @CacheEvict(allEntries = true)
    public void saveAll(List<ThirdPartyApiTokenItem> thirdPartyApiTokenItems) {
        logger.info("Batch saving {} ThirdPartyApiTokens", thirdPartyApiTokenItems.size());
        thirdPartyApiTokenRepository.saveAll(thirdPartyApiTokenItems);
    }
    
    /**
     * 批量删除Token
     * 
     * 注意：此操作会清除所有缓存
     * 
     * @param deletedThirdPartyApiTokenList 要删除的Token ID列表
     */
    @CacheEvict(allEntries = true)
    public void deleteAllById(List<String> deletedThirdPartyApiTokenList) {
        logger.info("Batch deleting {} ThirdPartyApiTokens", deletedThirdPartyApiTokenList.size());
        thirdPartyApiTokenRepository.deleteAllById(deletedThirdPartyApiTokenList);
    }
    
    /**
     * 检查Token是否存在
     * 
     * 用于验证第三方API连接时的Token认证
     * 
     * @param token 要检查的Token字符串
     * @return 如果Token存在返回true，否则返回false
     */
    public boolean existByToken(String token) {
        boolean exists = thirdPartyApiTokenRepository.existsByToken(token);
        logger.debug("Checking token existence: {}", exists ? "found" : "not found");
        return exists;
    }
    
    /**
     * 检查SID是否存在
     * 
     * 用于检查第三方API标识符是否已被使用
     * 
     * @param sid 要检查的SID
     * @return 如果SID存在返回true，否则返回false
     */
    public boolean existBySid(String sid) {
        boolean exists = thirdPartyApiTokenRepository.existsBySid(sid);
        logger.debug("Checking SID existence [{}]: {}", sid, exists ? "found" : "not found");
        return exists;
    }
}
