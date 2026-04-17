package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.qqverify.QQVerifyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface QQVerifyRecordRepository extends JpaRepository<QQVerifyRecord, String> {
    Optional<QQVerifyRecord> findFirstByQqOrderByVerifiedAtDesc(String qq);
}
