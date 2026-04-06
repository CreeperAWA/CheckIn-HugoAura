package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.rateLimit.RateLimitLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RateLimitLogRepository extends JpaRepository<RateLimitLog, String> {
    Page<RateLimitLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    List<RateLimitLog> findTop50ByOrderByCreatedAtDesc();
    
    @Query("SELECT l FROM RateLimitLog l WHERE l.createdAt >= :startTime ORDER BY l.createdAt DESC")
    List<RateLimitLog> findRecentLogs(@Param("startTime") LocalDateTime startTime);
    
    long countByCreatedAtAfter(LocalDateTime startTime);
    
    @Query("SELECT l.ipAddress as identifier, COUNT(l) as count FROM RateLimitLog l " +
           "WHERE l.createdAt >= :startTime AND l.ipAddress IS NOT NULL " +
           "GROUP BY l.ipAddress ORDER BY count DESC")
    List<Object[]> findTopLimitedIp(@Param("startTime") LocalDateTime startTime, Pageable pageable);
    
    @Query("SELECT l.qqNumber as identifier, COUNT(l) as count FROM RateLimitLog l " +
           "WHERE l.createdAt >= :startTime AND l.qqNumber IS NOT NULL " +
           "GROUP BY l.qqNumber ORDER BY count DESC")
    List<Object[]> findTopLimitedQq(@Param("startTime") LocalDateTime startTime, Pageable pageable);
    
    @Query("SELECT l.triggeredDimension as dimension, COUNT(l) as count FROM RateLimitLog l " +
           "WHERE l.createdAt >= :startTime GROUP BY l.triggeredDimension ORDER BY count DESC")
    List<Object[]> getStatsByDimension(@Param("startTime") LocalDateTime startTime);
}
