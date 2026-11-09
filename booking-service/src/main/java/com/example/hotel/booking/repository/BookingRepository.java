package com.example.hotel.booking.repository;

import com.example.hotel.booking.entity.Booking;
import com.example.hotel.booking.entity.Booking.Status;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
  Optional<Booking> findByUserIdAndRequestId(Long userId, UUID requestId);

  Page<Booking> findByUserId(Long userId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Booking b where b.id=:id")
  Optional<Booking> lock(@Param("id") UUID id);

  @Query(
      "select b.id from Booking b where b.releasePending=true or (b.status=:pending and b.createdAt<:before)")
  List<UUID> recoverable(
      @Param("pending") Status pending, @Param("before") Instant before, Pageable pageable);

  boolean existsByUserIdAndStatusNot(Long userId, Status status);
}
