package com.example.hotel.common;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public final class Contracts {
  private Contracts() {}

  public record RoomView(
      Long id, Long hotelId, String number, boolean available, long timesBooked) {}

  public record SlotRequest(
      @NotNull UUID bookingId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

  public record ReleaseRequest(@NotNull UUID bookingId) {}
}
