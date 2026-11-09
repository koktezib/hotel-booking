package com.example.hotel.booking.service;

import com.example.hotel.booking.api.BookingDtos.*;
import com.example.hotel.booking.client.HotelPort;
import com.example.hotel.booking.entity.Booking.Status;
import com.example.hotel.common.*;
import com.example.hotel.common.Contracts.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingSaga {
  private static final Logger log = LoggerFactory.getLogger(BookingSaga.class);
  private final BookingTransactions transactions;
  private final HotelPort hotel;

  public BookingView create(Long userId, CreateBooking in) {
    String trace =
        Optional.ofNullable(MDC.get("traceId")).orElseGet(() -> UUID.randomUUID().toString());
    Created result = transactions.begin(userId, in, trace);
    if (!result.fresh()) return result.booking();
    BookingView booking = result.booking();
    try {
      List<Long> candidates =
          in.autoSelect()
              ? hotel.recommend(in.hotelId(), in.startDate(), in.endDate()).stream()
                  .sorted(
                      Comparator.comparingLong(RoomView::timesBooked).thenComparing(RoomView::id))
                  .map(RoomView::id)
                  .toList()
              : List.of(in.roomId());
      for (Long roomId : candidates) {
        transactions.select(booking.id(), roomId);
        try {
          hotel.confirm(roomId, new SlotRequest(booking.id(), in.startDate(), in.endDate()));
        } catch (ApiException e) {
          if (in.autoSelect() && e.status() == HttpStatus.CONFLICT) continue;
          throw e;
        }
        BookingView confirmed = transactions.confirm(booking.id());
        if (confirmed.status() == Status.CANCELLED) compensate(confirmed);
        return transactions.get(booking.id());
      }
      throw ApiException.conflict("No available rooms for selected dates");
    } catch (ApiException e) {
      BookingView cancelled = transactions.cancel(booking.id(), e.status().value(), e.getMessage());
      compensate(cancelled);
      return transactions.get(booking.id());
    } catch (RuntimeException e) {
      log.error("event=saga_unexpected_failure bookingId={}", booking.id(), e);
      compensate(transactions.cancel(booking.id(), 503, "Booking failed; compensation scheduled"));
      return transactions.get(booking.id());
    }
  }

  public BookingView cancel(UUID id, Long owner) {
    transactions.owned(id, owner);
    compensate(transactions.cancel(id, null, "Cancelled by user"));
    return transactions.get(id);
  }

  public void compensate(BookingView booking) {
    if (!booking.releasePending() || booking.roomId() == null) return;
    try {
      hotel.release(booking.roomId(), booking.id());
      transactions.released(booking.id());
    } catch (ApiException e) {
      if (e.status() == HttpStatus.NOT_FOUND) {
        transactions.released(booking.id());
        return;
      }
      log.warn("event=compensation_deferred bookingId={} reason={}", booking.id(), e.getMessage());
    } catch (RuntimeException e) {
      log.error("event=compensation_deferred bookingId={}", booking.id(), e);
    }
  }
}
