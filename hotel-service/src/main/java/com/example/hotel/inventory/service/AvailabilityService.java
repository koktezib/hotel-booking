package com.example.hotel.inventory.service;

import com.example.hotel.common.*;
import com.example.hotel.common.Contracts.*;
import com.example.hotel.inventory.entity.*;
import com.example.hotel.inventory.repository.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvailabilityService {
  private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);
  private final RoomRepository rooms;
  private final ReservationRepository reservations;

  @Transactional
  public void confirm(Long roomId, SlotRequest in) {
    Dates.future(in.startDate(), in.endDate());
    Room room = lock(roomId);
    var existing = reservations.findById(in.bookingId());
    if (existing.isPresent()) {
      Reservation r = existing.get();
      if (r.isReleased()) throw ApiException.conflict("Booking has already been released");
      if (!r.getRoom().getId().equals(roomId)
          || !in.startDate().equals(r.getStartDate())
          || !in.endDate().equals(r.getEndDate()))
        throw ApiException.conflict("bookingId reused with different payload");
      log.info("event=slot_replay bookingId={} roomId={}", in.bookingId(), roomId);
      return;
    }
    if (!room.isAvailable() || reservations.overlaps(roomId, in.startDate(), in.endDate()))
      throw ApiException.conflict("Room unavailable for selected dates");
    reservations.save(new Reservation(in.bookingId(), room, in.startDate(), in.endDate(), false));
    room.setTimesBooked(room.getTimesBooked() + 1);
    log.info("event=slot_confirmed bookingId={} roomId={}", in.bookingId(), roomId);
  }

  @Transactional
  public void release(Long roomId, UUID bookingId) {
    Room room = lock(roomId);
    var existing = reservations.findById(bookingId);
    if (existing.isEmpty()) {

      reservations.save(new Reservation(bookingId, room, null, null, true));
    } else {
      Reservation r = existing.get();
      if (!r.getRoom().getId().equals(roomId))
        throw ApiException.conflict("bookingId belongs to another room");
      if (!r.isReleased()) {
        r.setReleased(true);
        room.setTimesBooked(room.getTimesBooked() - 1);
      }
    }
    log.info("event=slot_released bookingId={} roomId={}", bookingId, roomId);
  }

  private Room lock(Long id) {
    return rooms.lock(id).orElseThrow(() -> ApiException.notFound("Room not found"));
  }
}
