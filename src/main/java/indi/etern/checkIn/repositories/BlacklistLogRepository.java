package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.blacklist.BlacklistLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BlacklistLogRepository extends JpaRepository<BlacklistLog, String> {
    List<BlacklistLog> findByTargetIdOrderByOperatedAtDesc(String targetId);
}
