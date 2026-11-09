package com.example.hotel.inventory.repository;

import com.example.hotel.inventory.entity.Reservation;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
  @Query(
      "select count(b)>0 from Reservation b where b.room.id=:roomId and b.released=false and b.startDate<:end and b.endDate>:start")
  boolean overlaps(
      @Param("roomId") Long roomId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  boolean existsByRoomId(Long roomId);

  @Query(
      "select b from Reservation b join fetch b.room r where b.released=false and (:hotelId is null or r.hotel.id=:hotelId) and b.startDate<:end and b.endDate>:start")
  List<Reservation> inPeriod(
      @Param("hotelId") Long hotelId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
