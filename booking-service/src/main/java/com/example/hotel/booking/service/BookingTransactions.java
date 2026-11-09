package com.example.hotel.booking.service;

import com.example.hotel.booking.api.BookingDtos.*;
import com.example.hotel.booking.entity.*;
import com.example.hotel.booking.entity.Booking.Status;
import com.example.hotel.booking.repository.*;
import com.example.hotel.common.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingTransactions {
  private static final Logger log = LoggerFactory.getLogger(BookingTransactions.class);
  private final BookingRepository bookings;
  private final UserRepository users;

  @Transactional
  public Created begin(Long userId, CreateBooking in, String traceId) {

    UserAccount user =
        users
            .lock(userId)
            .filter(UserAccount::isEnabled)
            .orElseThrow(() -> ApiException.notFound("Active user not found"));
    var existing = bookings.findByUserIdAndRequestId(userId, in.requestId());
    if (existing.isPresent()) {
      Booking b = existing.get();
      if (b.isAutoSelect() != in.autoSelect()
          || !Objects.equals(b.getHotelId(), in.hotelId())
          || !b.getStartDate().equals(in.startDate())
          || !b.getEndDate().equals(in.endDate())
          || (!in.autoSelect() && !Objects.equals(b.getRoomId(), in.roomId())))
        throw ApiException.conflict("requestId reused with different payload");
      return new Created(view(b), false);
    }
    Dates.future(in.startDate(), in.endDate());
    if (!in.autoSelect() && (in.roomId() == null || in.roomId() < 1))
      throw ApiException.badRequest("Positive roomId required when autoSelect=false");
    Booking b = new Booking();
    b.setId(UUID.randomUUID());
    b.setUser(user);
    b.setRequestId(in.requestId());
    b.setRoomId(in.autoSelect() ? null : in.roomId());
    b.setHotelId(in.hotelId());
    b.setAutoSelect(in.autoSelect());
    b.setStartDate(in.startDate());
    b.setEndDate(in.endDate());
    b.setTraceId(traceId);
    bookings.saveAndFlush(b);
    log.info("event=booking_pending bookingId={} requestId={}", b.getId(), in.requestId());
    return new Created(view(b), true);
  }

  @Transactional
  public BookingView select(UUID id, Long roomId) {
    Booking b = lock(id);
    if (b.getStatus() != Status.PENDING) throw ApiException.conflict("Booking no longer pending");
    b.setRoomId(roomId);
    b.setUpdatedAt(Instant.now());
    return view(b);
  }

  @Transactional
  public BookingView confirm(UUID id) {
    Booking b = lock(id);
    if (b.getStatus() == Status.PENDING) {
      b.setStatus(Status.CONFIRMED);
      b.setUpdatedAt(Instant.now());
      log.info("event=booking_confirmed bookingId={}", id);
    }
    return view(b);
  }

  @Transactional
  public BookingView cancel(UUID id, Integer failureStatus, String reason) {
    Booking b = lock(id);
    if (b.getStatus() != Status.CANCELLED) {
      b.setStatus(Status.CANCELLED);
      b.setReleasePending(b.getRoomId() != null);
      b.setFailureStatus(failureStatus);
      b.setFailureReason(reason);
      b.setUpdatedAt(Instant.now());
      log.info(
          "event=booking_cancelled bookingId={} reason={} releasePending={}",
          id,
          reason,
          b.isReleasePending());
    }
    return view(b);
  }

  @Transactional
  public void released(UUID id) {
    Booking b = lock(id);
    b.setReleasePending(false);
    b.setUpdatedAt(Instant.now());
    log.info("event=compensation_complete bookingId={}", id);
  }

  @Transactional
  public BookingView expire(UUID id, Instant before) {
    Booking b = lock(id);
    if (b.getStatus() == Status.PENDING && b.getCreatedAt().isBefore(before)) {
      b.setStatus(Status.CANCELLED);
      b.setReleasePending(b.getRoomId() != null);
      b.setFailureStatus(504);
      b.setFailureReason("Pending saga expired");
      b.setUpdatedAt(Instant.now());
      log.info("event=booking_expired bookingId={}", id);
    }
    return view(b);
  }

  @Transactional(readOnly = true)
  public BookingView owned(UUID id, Long userId) {
    Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking not found"));
    if (!b.getUser().getId().equals(userId)) throw ApiException.notFound("Booking not found");
    return view(b);
  }

  @Transactional(readOnly = true)
  public BookingView get(UUID id) {
    return view(
        bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking not found")));
  }

  @Transactional(readOnly = true)
  public PageDto<BookingView> history(Long userId, int page, int size) {
    PageDto.validate(page, size);
    var p =
        bookings.findByUserId(
            userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    return new PageDto<>(
        p.map(this::view).getContent(), page, size, p.getTotalElements(), p.getTotalPages());
  }

  @Transactional(readOnly = true)
  public List<UUID> recoverable(Instant before) {
    return bookings.recoverable(
        Status.PENDING, before, PageRequest.of(0, 100, Sort.by("updatedAt")));
  }

  @Transactional(readOnly = true)
  public String trace(UUID id) {
    return bookings.findById(id).orElseThrow().getTraceId();
  }

  private Booking lock(UUID id) {
    return bookings.lock(id).orElseThrow(() -> ApiException.notFound("Booking not found"));
  }

  private BookingView view(Booking b) {
    return new BookingView(
        b.getId(),
        b.getRequestId(),
        b.getUser().getId(),
        b.getRoomId(),
        b.getHotelId(),
        b.isAutoSelect(),
        b.getStartDate(),
        b.getEndDate(),
        b.getStatus(),
        b.getCreatedAt(),
        b.isReleasePending(),
        b.getFailureStatus(),
        b.getFailureReason());
  }
}
