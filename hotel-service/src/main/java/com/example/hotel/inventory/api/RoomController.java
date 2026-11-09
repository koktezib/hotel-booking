package com.example.hotel.inventory.api;

import com.example.hotel.common.Contracts.*;
import com.example.hotel.inventory.api.HotelDtos.*;
import com.example.hotel.inventory.service.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RoomController {
  private final CatalogService catalog;
  private final AvailabilityService availability;

  @GetMapping
  @PreAuthorize("hasAnyRole('USER','ADMIN','INTERNAL')")
  public List<RoomView> free(
      @RequestParam(required = false) Long hotelId,
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate) {
    return catalog.free(hotelId, startDate, endDate, false);
  }

  @GetMapping("/recommend")
  @PreAuthorize("hasAnyRole('USER','ADMIN','INTERNAL')")
  public List<RoomView> recommend(
      @RequestParam(required = false) Long hotelId,
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate) {
    return catalog.free(hotelId, startDate, endDate, true);
  }

  @GetMapping("/all")
  @PreAuthorize("hasRole('ADMIN')")
  public List<RoomView> all(@RequestParam(required = false) Long hotelId) {
    return catalog.all(hotelId);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('USER','ADMIN')")
  public RoomView get(@PathVariable Long id) {
    return catalog.room(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public RoomView create(@Valid @RequestBody RoomInput in) {
    return catalog.createRoom(in);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public RoomView update(@PathVariable Long id, @Valid @RequestBody RoomUpdate in) {
    return catalog.updateRoom(id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable Long id) {
    catalog.deleteRoom(id);
  }

  @PostMapping("/{id}/confirm-availability")
  @PreAuthorize("hasRole('INTERNAL')")
  public void confirm(@PathVariable Long id, @Valid @RequestBody SlotRequest in) {
    availability.confirm(id, in);
  }

  @PostMapping("/{id}/release")
  @PreAuthorize("hasRole('INTERNAL')")
  public void release(@PathVariable Long id, @Valid @RequestBody ReleaseRequest in) {
    availability.release(id, in.bookingId());
  }
}
