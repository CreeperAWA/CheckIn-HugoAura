package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.rateLimit.RateLimitRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RateLimitRuleRepository extends JpaRepository<RateLimitRule, String> {
    List<RateLimitRule> findByEnabledTrueOrderByPriorityDesc();
    List<RateLimitRule> findAllByOrderByPriorityDesc();
    Optional<RateLimitRule> findByDimension(RateLimitRule.RateLimitDimension dimension);
}
