package com.example.hotel.booking.api;

import com.example.hotel.booking.entity.Booking.Status;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class BookingDtos {
  private BookingDtos() {}

  public record CreateBooking(
      @NotNull UUID requestId,
      Long roomId,
      @Positive Long hotelId,
      boolean autoSelect,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate) {}

  public record BookingView(
      UUID id,
      UUID requestId,
      Long userId,
      Long roomId,
      Long hotelId,
      boolean autoSelect,
      LocalDate startDate,
      LocalDate endDate,
      Status status,
      Instant createdAt,
      boolean releasePending,
      Integer failureStatus,
      String failureReason) {}

  public record Created(BookingView booking, boolean fresh) {}
}
