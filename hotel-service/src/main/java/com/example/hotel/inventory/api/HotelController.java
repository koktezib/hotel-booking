package com.example.hotel.inventory.api;

import com.example.hotel.common.*;
import com.example.hotel.inventory.api.HotelDtos.*;
import com.example.hotel.inventory.service.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class HotelController {
  private final CatalogService catalog;
  private final StatisticsService statistics;

  @GetMapping
  @PreAuthorize("hasAnyRole('USER','ADMIN')")
  public PageDto<HotelView> list(
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "id") String sort) {
    return catalog.hotels(q, page, size, sort);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('USER','ADMIN')")
  public HotelView get(@PathVariable Long id) {
    return catalog.hotel(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public HotelView create(@Valid @RequestBody HotelInput in) {
    return catalog.create(in);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public HotelView update(@PathVariable Long id, @Valid @RequestBody HotelInput in) {
    return catalog.update(id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable Long id) {
    catalog.deleteHotel(id);
  }

  @GetMapping("/statistics")
  @PreAuthorize("hasRole('ADMIN')")
  public Statistics stats(
      @RequestParam(required = false) Long hotelId,
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate,
      @RequestParam(defaultValue = "id") String sort) {
    return statistics.statistics(hotelId, startDate, endDate, sort);
  }
}
