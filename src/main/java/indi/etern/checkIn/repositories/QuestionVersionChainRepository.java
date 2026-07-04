package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.question.version.QuestionVersionChain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionVersionChainRepository extends JpaRepository<QuestionVersionChain, String> {
    List<QuestionVersionChain> findByVersionGroupIdOrderByCreatedAtDesc(String versionGroupId);
    List<QuestionVersionChain> findByFromVersionId(String fromVersionId);
    List<QuestionVersionChain> findByToVersionId(String toVersionId);
}
