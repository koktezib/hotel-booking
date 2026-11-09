package com.example.hotel.inventory.service;

import com.example.hotel.common.*;
import com.example.hotel.common.Contracts.RoomView;
import com.example.hotel.inventory.api.HotelDtos.*;
import com.example.hotel.inventory.entity.*;
import com.example.hotel.inventory.repository.*;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {
  private final HotelRepository hotels;
  private final RoomRepository rooms;
  private final ReservationRepository reservations;

  public PageDto<HotelView> hotels(String q, int page, int size, String sort) {
    PageDto.validate(page, size);
    if (!Set.of("id", "name", "address").contains(sort))
      throw ApiException.badRequest("sort: id, name or address");
    var result =
        hotels.findAll(
            (root, query, cb) ->
                q == null
                    ? cb.conjunction()
                    : cb.like(cb.lower(root.get("name")), "%" + q.toLowerCase(Locale.ROOT) + "%"),
            PageRequest.of(page, size, Sort.by(sort).ascending().and(Sort.by("id"))));
    return new PageDto<>(
        result.map(this::view).getContent(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }

  public HotelView hotel(Long id) {
    return view(requireHotel(id));
  }

  @Transactional
  public HotelView create(HotelInput in) {
    return view(hotels.save(new Hotel(in.name(), in.address())));
  }

  @Transactional
  public HotelView update(Long id, HotelInput in) {
    Hotel h = requireHotel(id);
    h.setName(in.name());
    h.setAddress(in.address());
    return view(h);
  }

  @Transactional
  public void deleteHotel(Long id) {
    Hotel h = requireHotel(id);
    if (rooms.existsByHotelId(id)) throw ApiException.conflict("Delete hotel rooms first");
    hotels.delete(h);
  }

  @Transactional
  public RoomView createRoom(RoomInput in) {
    Hotel h = requireHotel(in.hotelId());
    if (rooms.existsByHotelIdAndNumber(in.hotelId(), in.number()))
      throw ApiException.conflict("Room number already exists in hotel");
    try {
      return view(rooms.saveAndFlush(new Room(h, in.number(), in.available())));
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw ApiException.conflict("Room number already exists in hotel");
    }
  }

  @Transactional
  public RoomView updateRoom(Long id, RoomUpdate in) {
    Room r = lockedRoom(id);
    if (!r.getNumber().equals(in.number())
        && rooms.existsByHotelIdAndNumber(r.getHotel().getId(), in.number()))
      throw ApiException.conflict("Room number already exists in hotel");
    r.setNumber(in.number());
    r.setAvailable(in.available());
    try {
      rooms.flush();
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw ApiException.conflict("Room number already exists in hotel");
    }
    return view(r);
  }

  @Transactional
  public void deleteRoom(Long id) {
    Room r = lockedRoom(id);
    if (reservations.existsByRoomId(id))
      throw ApiException.conflict("Room has booking history; set available=false instead");
    rooms.delete(r);
  }

  public RoomView room(Long id) {
    return view(rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room not found")));
  }

  public List<RoomView> free(Long hotelId, LocalDate start, LocalDate end, boolean recommend) {
    Dates.future(start, end);
    var list = new ArrayList<>(rooms.free(hotelId, start, end));
    if (recommend)
      list.sort(Comparator.comparingLong(Room::getTimesBooked).thenComparing(Room::getId));
    return list.stream().map(this::view).toList();
  }

  public List<RoomView> all(Long hotelId) {
    return rooms.allWithHotel(hotelId).stream().map(this::view).toList();
  }

  private Hotel requireHotel(Long id) {
    return hotels.findById(id).orElseThrow(() -> ApiException.notFound("Hotel not found"));
  }

  private Room lockedRoom(Long id) {
    return rooms.lock(id).orElseThrow(() -> ApiException.notFound("Room not found"));
  }

  private HotelView view(Hotel h) {
    return new HotelView(h.getId(), h.getName(), h.getAddress());
  }

  private RoomView view(Room r) {
    return new RoomView(
        r.getId(), r.getHotel().getId(), r.getNumber(), r.isAvailable(), r.getTimesBooked());
  }
}
