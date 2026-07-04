package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.question.version.ScoreChangeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreChangeDetailRepository extends JpaRepository<ScoreChangeDetail, String> {
    List<ScoreChangeDetail> findByRecalculationLogId(String recalculationLogId);
}
