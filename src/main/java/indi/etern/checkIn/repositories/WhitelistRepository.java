package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.whitelist.Whitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WhitelistRepository extends JpaRepository<Whitelist, String> {
    boolean existsByTargetId(String targetId);
    Optional<Whitelist> findByTargetId(String targetId);
    void deleteByTargetId(String targetId);
}
