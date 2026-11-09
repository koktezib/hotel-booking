package com.example.hotel.booking.client;

import com.example.hotel.common.Contracts.*;
import java.time.LocalDate;
import java.util.*;

public interface HotelPort {
  List<RoomView> recommend(Long hotelId, LocalDate start, LocalDate end);

  void confirm(Long roomId, SlotRequest request);

  void release(Long roomId, UUID bookingId);
}
