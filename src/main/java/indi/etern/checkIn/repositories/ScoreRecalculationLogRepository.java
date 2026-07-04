package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.question.version.ScoreRecalculationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreRecalculationLogRepository extends JpaRepository<ScoreRecalculationLog, String> {
    List<ScoreRecalculationLog> findByQuestionIdOrderByTriggeredAtDesc(String questionId);
    Page<ScoreRecalculationLog> findAllByOrderByTriggeredAtDesc(Pageable pageable);
}
