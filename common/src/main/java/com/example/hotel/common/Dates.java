package com.example.hotel.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class Dates {
  private Dates() {}

  public static void range(LocalDate start, LocalDate end) {
    if (start == null || end == null || !end.isAfter(start))
      throw ApiException.badRequest("endDate must be after startDate");
    if (ChronoUnit.DAYS.between(start, end) > 366)
      throw ApiException.badRequest("Maximum interval is 366 nights");
  }

  public static void future(LocalDate start, LocalDate end) {
    range(start, end);
    if (start.isBefore(LocalDate.now(java.time.ZoneOffset.UTC)))
      throw ApiException.badRequest("startDate must not be in the past (UTC)");
  }
}
