package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.thirdPartyApiToken.ThirdPartyApiTokenItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThirdPartyApiTokenRepository extends JpaRepository<ThirdPartyApiTokenItem, String> {
    boolean existsByToken(String token);
    boolean existsBySid(String sid);
}
