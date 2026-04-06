package indi.etern.checkIn.service.dao;

import indi.etern.checkIn.entities.rateLimit.RateLimitLog;
import indi.etern.checkIn.repositories.RateLimitLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RateLimitLogService {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitLogService.class);
    private static final int BATCH_SIZE = 100;
    
    private final RateLimitLogRepository logRepository;
    private final ConcurrentLinkedQueue<RateLimitLog> pendingLogs = new ConcurrentLinkedQueue<>();
    
    public RateLimitLogService(RateLimitLogRepository logRepository) {
        this.logRepository = logRepository;
    }
    
    @Async
    public void logRateLimitAsync(RateLimitLog logEntry) {
        pendingLogs.add(logEntry);
        if (pendingLogs.size() >= BATCH_SIZE) {
            flushPendingLogs();
        }
    }
    
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 5000)
    public void scheduledFlush() {
        flushPendingLogs();
    }
    
    @Transactional
    public synchronized void flushPendingLogs() {
        if (pendingLogs.isEmpty()) return;
        
        List<RateLimitLog> batch = new ArrayList<>();
        while (!pendingLogs.isEmpty() && batch.size() < BATCH_SIZE) {
            RateLimitLog log = pendingLogs.poll();
            if (log != null) {
                batch.add(log);
            }
        }
        
        if (!batch.isEmpty()) {
            try {
                logRepository.saveAll(batch);
                logger.debug("Flushed {} rate limit logs to database", batch.size());
            } catch (Exception e) {
                logger.error("Failed to flush rate limit logs", e);
                pendingLogs.addAll(batch);
            }
        }
    }
    
    @Transactional(readOnly = true)
    public Page<RateLimitLog> getRecentLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return logRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
    
    @Transactional(readOnly = true)
    public List<RateLimitLog> getLatestLogs(int limit) {
        return logRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .limit(limit)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public List<RateLimitLog> getLogsSince(LocalDateTime startTime) {
        return logRepository.findRecentLogs(startTime);
    }
    
    @Transactional(readOnly = true)
    public long getTotalCountSince(LocalDateTime startTime) {
        return logRepository.countByCreatedAtAfter(startTime);
    }
    
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(LocalDateTime startTime) {
        Map<String, Object> stats = new HashMap<>();
        
        long totalCount = logRepository.countByCreatedAtAfter(startTime);
        stats.put("totalCount", totalCount);
        
        List<Object[]> dimensionStats = logRepository.getStatsByDimension(startTime);
        Map<String, Long> byDimension = new HashMap<>();
        for (Object[] row : dimensionStats) {
            String dimension = ((Enum<?>) row[0]).name();
            Long count = (Long) row[1];
            byDimension.put(dimension, count);
        }
        stats.put("byDimension", byDimension);
        
        Pageable top10 = PageRequest.of(0, 10);
        List<Object[]> topIps = logRepository.findTopLimitedIp(startTime, top10);
        List<Map<String, Object>> topIpList = new ArrayList<>();
        for (Object[] row : topIps) {
            Map<String, Object> item = new HashMap<>();
            item.put("identifier", row[0]);
            item.put("count", row[1]);
            topIpList.add(item);
        }
        stats.put("topIps", topIpList);
        
        List<Object[]> topQqs = logRepository.findTopLimitedQq(startTime, top10);
        List<Map<String, Object>> topQqList = new ArrayList<>();
        for (Object[] row : topQqs) {
            Map<String, Object> item = new HashMap<>();
            item.put("identifier", row[0]);
            item.put("count", row[1]);
            topQqList.add(item);
        }
        stats.put("topQqs", topQqList);
        
        return stats;
    }
}
