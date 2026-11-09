package com.example.hotel.booking.api;

import com.example.hotel.booking.api.BookingDtos.*;
import com.example.hotel.booking.entity.Booking.Status;
import com.example.hotel.booking.service.*;
import com.example.hotel.common.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN') and @accountAccess.active(authentication)")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {
  private final BookingSaga saga;
  private final BookingTransactions transactions;

  @PostMapping({"/booking", "/api/bookings"})
  public ResponseEntity<?> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateBooking in,
      jakarta.servlet.http.HttpServletRequest request) {
    BookingView b = saga.create(Long.valueOf(jwt.getSubject()), in);
    int status =
        b.status() == Status.PENDING
            ? 202
            : b.status() == Status.CANCELLED
                ? (b.failureStatus() == null ? 200 : b.failureStatus())
                : 200;
    Object body =
        status >= 400
            ? new ErrorDto(
                java.time.Instant.now(),
                status,
                HttpStatus.valueOf(status).getReasonPhrase(),
                b.failureReason() + "; bookingId=" + b.id(),
                request.getRequestURI(),
                (String) request.getAttribute("traceId"))
            : b;
    return ResponseEntity.status(status)
        .header(HttpHeaders.LOCATION, "/api/bookings/" + b.id())
        .body(body);
  }

  @GetMapping({"/bookings", "/api/bookings"})
  public PageDto<BookingView> history(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return transactions.history(Long.valueOf(jwt.getSubject()), page, size);
  }

  @GetMapping({"/booking/{id}", "/api/bookings/{id}"})
  public BookingView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return transactions.owned(id, Long.valueOf(jwt.getSubject()));
  }

  @DeleteMapping({"/booking/{id}", "/api/bookings/{id}"})
  public BookingView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return saga.cancel(id, Long.valueOf(jwt.getSubject()));
  }
}
