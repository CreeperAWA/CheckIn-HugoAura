package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.answerLimit.AnswerLimitWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AnswerLimitWhitelistRepository extends JpaRepository<AnswerLimitWhitelist, String> {
    boolean existsByQq(String qq);
    Optional<AnswerLimitWhitelist> findByQq(String qq);
    void deleteByQq(String qq);
}
