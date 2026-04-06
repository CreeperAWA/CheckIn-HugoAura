package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RateLimitWhitelistRepository extends JpaRepository<RateLimitWhitelist, String> {
    List<RateLimitWhitelist> findByDimension(RateLimitWhitelist.WhitelistDimension dimension);
    boolean existsByDimensionAndWhitelistValue(RateLimitWhitelist.WhitelistDimension dimension, String whitelistValue);
    Optional<RateLimitWhitelist> findByDimensionAndWhitelistValue(RateLimitWhitelist.WhitelistDimension dimension, String whitelistValue);
}
