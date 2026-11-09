package com.example.hotel.booking.repository;

import com.example.hotel.booking.entity.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByUsername(String username);

  boolean existsByUsername(String username);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.id=:id")
  Optional<UserAccount> lock(@Param("id") Long id);
}
