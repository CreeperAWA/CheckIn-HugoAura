package indi.etern.checkIn.service.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 验证状态缓存服务
 *
 * 使用 Caffeine 缓存管理 QQ 验证状态，提供：
 * 1. 线程安全的并发访问
 * 2. 自动过期清理（写入后 10 分钟过期）
 * 3. LRU 容量限制（最多 1000 条）
 */
@Service
public class VerifyStatusCacheService {

    private static final Logger logger = LoggerFactory.getLogger(VerifyStatusCacheService.class);

    /**
     * 验证状态信息
     */
    public static class VerifyStatusInfo {
        private final String status;
        private final String guideMessage;
        private final String verifyContent;
        private final long createdAt;

        public VerifyStatusInfo(String status, String guideMessage, String verifyContent) {
            this.status = status;
            this.guideMessage = guideMessage;
            this.verifyContent = verifyContent;
            this.createdAt = System.currentTimeMillis();
        }

        public String getStatus() {
            return status;
        }

        public String getGuideMessage() {
            return guideMessage;
        }

        public String getVerifyContent() {
            return verifyContent;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    private final Cache<String, VerifyStatusInfo> cache;

    public VerifyStatusCacheService() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * 获取验证状态
     *
     * @param qq QQ号字符串
     * @return 验证状态信息，不存在或已过期时返回 null
     */
    public VerifyStatusInfo get(String qq) {
        return cache.getIfPresent(qq);
    }

    /**
     * 写入验证状态
     *
     * @param qq             QQ号字符串
     * @param status         验证状态
     * @param guideMessage   引导消息
     * @param verifyContent  验证内容
     */
    public void put(String qq, String status, String guideMessage, String verifyContent) {
        cache.put(qq, new VerifyStatusInfo(status, guideMessage, verifyContent));
    }

    /**
     * 移除验证状态
     *
     * @param qq QQ号字符串
     */
    public void remove(String qq) {
        cache.invalidate(qq);
    }

    /**
     * 获取缓存中的条目数量（估计值）
     */
    public long getSize() {
        return cache.estimatedSize();
    }
}
