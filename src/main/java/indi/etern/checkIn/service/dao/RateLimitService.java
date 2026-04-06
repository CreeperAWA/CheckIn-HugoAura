package indi.etern.checkIn.service.dao;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import indi.etern.checkIn.entities.rateLimit.RateLimitRule;
import indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist;
import indi.etern.checkIn.repositories.RateLimitRuleRepository;
import indi.etern.checkIn.repositories.RateLimitWhitelistRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {
    private final RateLimitRuleRepository ruleRepository;
    private final RateLimitWhitelistRepository whitelistRepository;
    
    private Cache<String, AtomicInteger> requestCounters;
    private List<RateLimitRule> cachedRules;
    private Map<String, Boolean> whitelistCache;
    
    public RateLimitService(RateLimitRuleRepository ruleRepository,
                           RateLimitWhitelistRepository whitelistRepository) {
        this.ruleRepository = ruleRepository;
        this.whitelistRepository = whitelistRepository;
    }
    
    @PostConstruct
    public void init() {
        requestCounters = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfter(new Expiry<String, AtomicInteger>() {
                    @Override
                    public long expireAfterCreate(String key, AtomicInteger value, long currentTime) {
                        return getTimeWindowForKey(key);
                    }
                    
                    @Override
                    public long expireAfterUpdate(String key, AtomicInteger value, long currentTime, long currentDuration) {
                        return getTimeWindowForKey(key);
                    }
                    
                    @Override
                    public long expireAfterRead(String key, AtomicInteger value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
        
        loadRules();
        loadWhitelist();
    }
    
    @Scheduled(fixedRate = 30000)
    public void refreshCache() {
        loadRules();
        loadWhitelist();
    }
    
    @Transactional(readOnly = true)
    public void loadRules() {
        cachedRules = ruleRepository.findAllByOrderByPriorityDesc();
    }
    
    @Transactional(readOnly = true)
    public void loadWhitelist() {
        List<RateLimitWhitelist> allWhitelists = whitelistRepository.findAll();
        whitelistCache = new ConcurrentHashMap<>();
        for (RateLimitWhitelist item : allWhitelists) {
            String cacheKey = item.getDimension().name() + ":" + item.getWhitelistValue();
            whitelistCache.put(cacheKey, true);
        }
    }
    
    public boolean isWhitelisted(RateLimitWhitelist.WhitelistDimension dimension, String value) {
        if (value == null || value.isEmpty()) return false;
        String cacheKey = dimension.name() + ":" + value;
        return whitelistCache.containsKey(cacheKey);
    }
    
    public List<RateLimitRule> getEnabledRules() {
        return cachedRules.stream()
                .filter(RateLimitRule::isEnabled)
                .toList();
    }
    
    public List<RateLimitRule> getAllRules() {
        return cachedRules;
    }
    
    public RateLimitCheckResult checkRateLimit(String identifier, RateLimitRule rule) {
        String cacheKey = buildCacheKeyWithRuleId(rule.getId(), identifier);
        AtomicInteger counter = requestCounters.get(cacheKey, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();
        
        if (currentCount > rule.getMaxRequests()) {
            return new RateLimitCheckResult(false, currentCount, rule);
        }
        return new RateLimitCheckResult(true, currentCount, rule);
    }
    
    public int getCurrentCount(String identifier, RateLimitRule rule) {
        String cacheKey = buildCacheKeyWithRuleId(rule.getId(), identifier);
        AtomicInteger counter = requestCounters.getIfPresent(cacheKey);
        if (counter == null) {
            return 0;
        }
        return counter.get();
    }
    
    private String buildCacheKey(RateLimitRule.RateLimitDimension dimension, String value) {
        return "rate_limit:" + dimension.name() + ":" + value;
    }
    
    private String buildCacheKeyWithRuleId(String ruleId, String identifier) {
        return "rate_limit:" + ruleId + ":" + identifier;
    }
    
    private long getTimeWindowForKey(String key) {
        String[] parts = key.split(":");
        if (parts.length >= 3) {
            String ruleId = parts[1];
            for (RateLimitRule rule : cachedRules) {
                if (rule.getId().equals(ruleId) && rule.isEnabled()) {
                    return TimeUnit.SECONDS.toNanos(rule.getTimeWindowSeconds());
                }
            }
        }
        return TimeUnit.MINUTES.toNanos(1);
    }
    
    @Transactional
    public void saveRules(List<RateLimitRule> rules) {
        ruleRepository.saveAll(rules);
        loadRules();
    }
    
    @Transactional
    public void saveWhitelistItem(RateLimitWhitelist item) {
        whitelistRepository.save(item);
        loadWhitelist();
    }
    
    @Transactional
    public void deleteWhitelistItem(String id) {
        whitelistRepository.deleteById(id);
        loadWhitelist();
    }
    
    public List<RateLimitWhitelist> getAllWhitelistItems() {
        return whitelistRepository.findAll();
    }
    
    public void clearCounter(String identifier, RateLimitRule.RateLimitDimension dimension) {
        String cacheKey = buildCacheKey(dimension, identifier);
        requestCounters.invalidate(cacheKey);
    }
    
    public record RateLimitCheckResult(boolean allowed, int currentCount, RateLimitRule rule) {}
}
