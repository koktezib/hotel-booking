package com.example.hotel.inventory.repository;

import com.example.hotel.inventory.entity.Room;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Room r where r.id=:id")
  Optional<Room> lock(@Param("id") Long id);

  boolean existsByHotelIdAndNumber(Long hotelId, String number);

  boolean existsByHotelId(Long hotelId);

  @Query(
      "select r from Room r join fetch r.hotel where (:hotelId is null or r.hotel.id=:hotelId) and r.available=true and not exists (select b.id from Reservation b where b.room=r and b.released=false and b.startDate<:end and b.endDate>:start)")
  List<Room> free(
      @Param("hotelId") Long hotelId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query("select r from Room r join fetch r.hotel where (:hotelId is null or r.hotel.id=:hotelId)")
  List<Room> allWithHotel(@Param("hotelId") Long hotelId);
}
