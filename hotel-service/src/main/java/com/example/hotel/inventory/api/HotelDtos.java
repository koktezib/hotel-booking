package com.example.hotel.inventory.api;

import jakarta.validation.constraints.*;

public final class HotelDtos {
  private HotelDtos() {}

  public record HotelInput(
      @NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 250) String address) {}

  public record HotelView(Long id, String name, String address) {}

  public record RoomInput(
      @NotNull @Positive Long hotelId,
      @NotBlank @Size(max = 30) String number,
      @NotNull Boolean available) {}

  public record RoomUpdate(@NotBlank @Size(max = 30) String number, @NotNull Boolean available) {}

  public record RoomStats(
      Long roomId,
      Long hotelId,
      String number,
      boolean available,
      long timesBooked,
      long bookingsInPeriod,
      long occupiedNights,
      long periodNights,
      double occupancyPercent) {}

  public record Statistics(
      java.time.LocalDate startDate,
      java.time.LocalDate endDate,
      long totalRooms,
      long operationalRooms,
      long occupiedNights,
      double occupancyPercent,
      java.util.List<RoomStats> rooms) {}
}
