package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.blacklist.Blacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, String> {
    boolean existsByTargetId(String targetId);
    Optional<Blacklist> findByTargetId(String targetId);
    void deleteByTargetId(String targetId);
}
