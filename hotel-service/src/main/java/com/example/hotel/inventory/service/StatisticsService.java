package com.example.hotel.inventory.service;

import com.example.hotel.common.*;
import com.example.hotel.inventory.api.HotelDtos.*;
import com.example.hotel.inventory.entity.*;
import com.example.hotel.inventory.repository.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatisticsService {
  private final RoomRepository rooms;
  private final ReservationRepository reservations;

  @Transactional(readOnly = true)
  public Statistics statistics(Long hotelId, LocalDate start, LocalDate end, String sort) {
    Dates.range(start, end);
    long days = ChronoUnit.DAYS.between(start, end);
    var slots = reservations.inPeriod(hotelId, start, end);
    var stats = new ArrayList<RoomStats>();
    for (Room room : rooms.allWithHotel(hotelId)) {
      long nights = 0, count = 0;
      for (Reservation r : slots)
        if (r.getRoom().getId().equals(room.getId())) {
          LocalDate a = r.getStartDate().isBefore(start) ? start : r.getStartDate();
          LocalDate b = r.getEndDate().isAfter(end) ? end : r.getEndDate();
          nights += ChronoUnit.DAYS.between(a, b);
          count++;
        }
      stats.add(
          new RoomStats(
              room.getId(),
              room.getHotel().getId(),
              room.getNumber(),
              room.isAvailable(),
              room.getTimesBooked(),
              count,
              nights,
              days,
              100.0 * nights / days));
    }
    Comparator<RoomStats> comparator =
        switch (sort) {
          case "timesBooked" -> Comparator.comparingLong(RoomStats::timesBooked);
          case "occupancy" -> Comparator.comparingDouble(RoomStats::occupancyPercent).reversed();
          case "id" -> Comparator.comparing(RoomStats::roomId);
          default -> throw ApiException.badRequest("sort: id, timesBooked or occupancy");
        };
    stats.sort(comparator.thenComparing(RoomStats::roomId));
    long occupied = stats.stream().mapToLong(RoomStats::occupiedNights).sum();
    return new Statistics(
        start,
        end,
        stats.size(),
        stats.stream().filter(RoomStats::available).count(),
        occupied,
        stats.isEmpty() ? 0 : 100.0 * occupied / (stats.size() * days),
        stats);
  }
}
