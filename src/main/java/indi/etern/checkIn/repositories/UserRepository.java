package indi.etern.checkIn.repositories;

import indi.etern.checkIn.entities.user.OAuth2Binding;
import indi.etern.checkIn.entities.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findAllByName(String username);
    boolean existsByName(String name);
}