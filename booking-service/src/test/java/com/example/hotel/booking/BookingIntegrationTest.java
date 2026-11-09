package com.example.hotel.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.hotel.booking.api.BookingDtos.*;
import com.example.hotel.booking.client.HotelPort;
import com.example.hotel.booking.entity.*;
import com.example.hotel.booking.entity.Booking.Status;
import com.example.hotel.booking.repository.*;
import com.example.hotel.booking.service.*;
import com.example.hotel.common.*;
import com.example.hotel.common.Contracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:booking-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "saga.recovery-delay-ms=3600000"
    })
@AutoConfigureMockMvc
class BookingIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired BookingRepository bookings;
  @Autowired BookingSaga saga;
  @Autowired BookingTransactions transactions;
  @Autowired SagaRecovery recovery;
  @Autowired PasswordEncoder passwords;
  @MockitoBean HotelPort hotel;
  Long alice, bob, admin;
  final LocalDate start = LocalDate.now(ZoneOffset.UTC).plusDays(7), end = start.plusDays(2);

  @BeforeEach
  void setup() {
    bookings.deleteAll();
    users.deleteAll();
    reset(hotel);
    alice =
        users
            .save(new UserAccount("alice", passwords.encode("Alice123!"), UserAccount.Role.USER))
            .getId();
    bob =
        users
            .save(new UserAccount("bob", passwords.encode("Bob12345!"), UserAccount.Role.USER))
            .getId();
    admin =
        users
            .save(new UserAccount("admin", passwords.encode("Admin123!"), UserAccount.Role.ADMIN))
            .getId();
  }

  private CreateBooking request(UUID requestId, boolean auto) {
    return new CreateBooking(requestId, 1L, null, auto, start, end);
  }

  private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .JwtRequestPostProcessor
      as(Long id, String role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Test
  void successCommitsPendingBeforeRemoteCallAndConfirms() {
    doAnswer(
            call -> {
              var slot = call.getArgument(1, SlotRequest.class);
              assertThat(bookings.findById(slot.bookingId()).orElseThrow().getStatus())
                  .isEqualTo(Status.PENDING);
              return null;
            })
        .when(hotel)
        .confirm(anyLong(), any());
    var b = saga.create(alice, request(UUID.randomUUID(), false));
    assertThat(b.status()).isEqualTo(Status.CONFIRMED);
    verify(hotel, never()).release(anyLong(), any());
  }

  @Test
  void remoteFailureCancelsAndCompensates() {
    doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel down"))
        .when(hotel)
        .confirm(anyLong(), any());
    var b = saga.create(alice, request(UUID.randomUUID(), false));
    assertThat(b.status()).isEqualTo(Status.CANCELLED);
    assertThat(b.failureStatus()).isEqualTo(503);
    assertThat(b.releasePending()).isFalse();
    verify(hotel).release(1L, b.id());
  }

  @Test
  void timeoutAndFailedReleaseAreRecoveredLater() {
    doThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT, "Timeout"))
        .when(hotel)
        .confirm(anyLong(), any());
    doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Release unavailable"))
        .when(hotel)
        .release(anyLong(), any());
    var b = saga.create(alice, request(UUID.randomUUID(), false));
    assertThat(b.status()).isEqualTo(Status.CANCELLED);
    assertThat(b.releasePending()).isTrue();
    doNothing().when(hotel).release(anyLong(), any());
    recovery.recover();
    assertThat(transactions.get(b.id()).releasePending()).isFalse();
    verify(hotel, times(2)).release(1L, b.id());
  }

  @Test
  void stalePendingIsCancelledButConfirmedBookingDoesNotExpire() {
    var pending =
        transactions.begin(alice, request(UUID.randomUUID(), false), "recovery-test").booking();
    Booking row = bookings.findById(pending.id()).orElseThrow();
    row.setCreatedAt(Instant.now().minusSeconds(600));
    bookings.save(row);
    recovery.recover();
    assertThat(transactions.get(pending.id()).status()).isEqualTo(Status.CANCELLED);
    verify(hotel).release(1L, pending.id());
    var confirmed = saga.create(alice, request(UUID.randomUUID(), false));
    assertThat(transactions.expire(confirmed.id(), Instant.now().plusSeconds(600)).status())
        .isEqualTo(Status.CONFIRMED);
  }

  @Test
  void replayDoesNotCreateDuplicatesOrCallHotelTwice() {
    UUID id = UUID.randomUUID();
    var first = saga.create(alice, request(id, false));
    var second = saga.create(alice, request(id, false));
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(bookings.count()).isEqualTo(1);
    verify(hotel, times(1)).confirm(anyLong(), any());
    assertThatThrownBy(() -> saga.create(alice, new CreateBooking(id, 2L, null, false, start, end)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void parallelReplayCreatesOneBookingAndOneRemoteConfirmation() throws Exception {
    UUID requestId = UUID.randomUUID();
    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      List<Callable<BookingView>> jobs = new ArrayList<>();
      for (int i = 0; i < 30; i++) jobs.add(() -> saga.create(alice, request(requestId, false)));
      Set<UUID> ids = new HashSet<>();
      for (var f : pool.invokeAll(jobs)) ids.add(f.get(15, TimeUnit.SECONDS).id());
      assertThat(ids).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(bookings.count()).isEqualTo(1);
    verify(hotel, times(1)).confirm(anyLong(), any());
  }

  @Test
  void autoSelectionIgnoresRoomIdSortsByLoadAndFallsBackAfterConflict() {
    when(hotel.recommend(null, start, end))
        .thenReturn(
            List.of(
                new RoomView(5L, 1L, "5", true, 9),
                new RoomView(3L, 1L, "3", true, 0),
                new RoomView(2L, 1L, "2", true, 0)));
    doThrow(ApiException.conflict("Race lost")).when(hotel).confirm(eq(2L), any());
    var b = saga.create(alice, new CreateBooking(UUID.randomUUID(), 999L, null, true, start, end));
    assertThat(b.roomId()).isEqualTo(3L);
    assertThat(b.status()).isEqualTo(Status.CONFIRMED);
    var ordered = inOrder(hotel);
    ordered.verify(hotel).recommend(null, start, end);
    ordered.verify(hotel).confirm(eq(2L), any());
    ordered.verify(hotel).confirm(eq(3L), any());
    verify(hotel, never()).confirm(eq(999L), any());
  }

  @Test
  void cancellationIsIdempotentAndAnotherUserCannotReadOrCancel() throws Exception {
    var b = saga.create(alice, request(UUID.randomUUID(), false));
    mvc.perform(get("/api/bookings/" + b.id()).with(as(bob, "USER")))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/bookings/" + b.id()).with(as(bob, "USER")))
        .andExpect(status().isNotFound());
    saga.cancel(b.id(), alice);
    saga.cancel(b.id(), alice);
    verify(hotel, times(1)).release(1L, b.id());
    assertThat(transactions.get(b.id()).status()).isEqualTo(Status.CANCELLED);
  }

  @Test
  void historyIsPaginatedAndOnlyContainsOwnBookings() throws Exception {
    for (int i = 0; i < 3; i++) saga.create(alice, request(UUID.randomUUID(), false));
    saga.create(bob, request(UUID.randomUUID(), false));
    mvc.perform(get("/api/bookings?page=1&size=2").with(as(alice, "USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content.length()").value(1));
    mvc.perform(get("/api/bookings?size=101").with(as(alice, "USER")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void authenticationRegistrationAndAdminRestrictions() throws Exception {
    mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
    String response =
        mvc.perform(
                post("/api/auth/register")
                    .contentType("application/json")
                    .content(
                        "{\"username\":\"newuser\",\"password\":\"Password123!\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = mapper.readTree(response).get("accessToken").asText();
    mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"newuser\",\"password\":\"Password123!\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"alice\",\"password\":\"Wrong123!\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"alice\",\"password\":\"Alice123!\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void adminUserCrudAndDisabledAccountAccess() throws Exception {
    mvc.perform(get("/api/users").with(as(admin, "ADMIN"))).andExpect(status().isOk());
    mvc.perform(
            patch("/api/users/" + bob)
                .with(as(admin, "ADMIN"))
                .contentType("application/json")
                .content("{\"username\":\"robert\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("robert"));
    mvc.perform(delete("/api/users/" + bob).with(as(admin, "ADMIN")))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/bookings").with(as(bob, "USER"))).andExpect(status().isForbidden());
  }

  @Test
  void invalidDatesAndMissingRoomDoNotStartSaga() throws Exception {
    var invalid = new CreateBooking(UUID.randomUUID(), 1L, null, false, end, start);
    mvc.perform(
            post("/api/bookings")
                .with(as(alice, "USER"))
                .contentType("application/json")
                .content(mapper.writeValueAsString(invalid)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/bookings")
                .with(as(alice, "USER"))
                .contentType("application/json")
                .content(
                    mapper.writeValueAsString(
                        new CreateBooking(UUID.randomUUID(), null, null, false, start, end))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(hotel);
    assertThat(bookings.count()).isZero();
  }

  @Test
  void failedSagaReturnsErrorDtoAndLocationOfCancelledBooking() throws Exception {
    doThrow(ApiException.conflict("Occupied")).when(hotel).confirm(anyLong(), any());
    mvc.perform(
            post("/api/bookings")
                .with(as(alice, "USER"))
                .contentType("application/json")
                .content(mapper.writeValueAsString(request(UUID.randomUUID(), false))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(header().exists("Location"));
    assertThat(bookings.findAll()).extracting(Booking::getStatus).containsExactly(Status.CANCELLED);
  }

  @Test
  void cancellationWhileConfirmationIsInFlightCannotReviveBooking() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
    doAnswer(
            call -> {
              entered.countDown();
              assertThat(finish.await(10, TimeUnit.SECONDS)).isTrue();
              return null;
            })
        .when(hotel)
        .confirm(anyLong(), any());
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<BookingView> creation =
          pool.submit(() -> saga.create(alice, request(UUID.randomUUID(), false)));
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      UUID id = bookings.findAll().get(0).getId();
      assertThat(saga.cancel(id, alice).status()).isEqualTo(Status.CANCELLED);
      finish.countDown();
      assertThat(creation.get(10, TimeUnit.SECONDS).status()).isEqualTo(Status.CANCELLED);
      verify(hotel).release(1L, id);
    } finally {
      finish.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void sameIdempotencyKeyIsScopedToItsOwner() {
    UUID key = UUID.randomUUID();
    assertThat(saga.create(alice, request(key, false)).id())
        .isNotEqualTo(saga.create(bob, request(key, false)).id());
    assertThat(bookings.count()).isEqualTo(2);
  }

  @Test
  void noCandidatesCancelsWithoutRemoteRelease() {
    when(hotel.recommend(null, start, end)).thenReturn(List.of());
    var b = saga.create(alice, request(UUID.randomUUID(), true));
    assertThat(b.status()).isEqualTo(Status.CANCELLED);
    assertThat(b.failureStatus()).isEqualTo(409);
    verify(hotel, never()).release(anyLong(), any());
    verify(hotel, never()).confirm(anyLong(), any());
  }
}
