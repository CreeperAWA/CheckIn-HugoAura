package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.httpApiToken.HttpApiTokenItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HttpApiTokenRepository extends JpaRepository<HttpApiTokenItem,String> {
    boolean existsByToken(String token);
}