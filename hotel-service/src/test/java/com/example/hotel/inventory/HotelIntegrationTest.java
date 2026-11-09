package com.example.hotel.inventory;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.hotel.common.*;
import com.example.hotel.common.Contracts.*;
import com.example.hotel.inventory.entity.*;
import com.example.hotel.inventory.repository.*;
import com.example.hotel.inventory.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:hotel-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
    })
@AutoConfigureMockMvc
class HotelIntegrationTest {
  @Test
  void frameworkErrorsKeepCorrectStatusAndErrorDto() throws Exception {
    var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    mvc.perform(get("/api/nonexistent").with(admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
    mvc.perform(patch("/api/hotels").with(admin))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"))
        .andExpect(jsonPath("$.status").value(405));
  }

  @Autowired MockMvc mvc;
  @Autowired HotelRepository hotels;
  @Autowired RoomRepository rooms;
  @Autowired ReservationRepository reservations;
  @Autowired AvailabilityService availability;
  @Autowired CatalogService catalog;
  @Autowired StatisticsService statistics;
  @Autowired JwtEncoder encoder;
  Long hotelId, roomId, secondRoomId;
  final LocalDate start = LocalDate.now(ZoneOffset.UTC).plusDays(5), end = start.plusDays(3);

  @BeforeEach
  void setup() {
    reservations.deleteAll();
    rooms.deleteAll();
    hotels.deleteAll();
    Hotel hotel = hotels.save(new Hotel("Test Hotel", "Test address"));
    hotelId = hotel.getId();
    roomId = rooms.save(new Room(hotel, "101", true)).getId();
    secondRoomId = rooms.save(new Room(hotel, "102", true)).getId();
    rooms.save(new Room(hotel, "repair", false));
  }

  @Test
  void unauthenticatedAndWrongRoleAreRejected() throws Exception {
    mvc.perform(get("/api/hotels"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(header().exists("X-Trace-Id"));
    mvc.perform(
            post("/api/hotels")
                .with(jwt())
                .contentType("application/json")
                .content("{\"name\":\"X\",\"address\":\"Y\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void realJwtIsValidatedAndInvalidTokenRejected() throws Exception {
    String token = JwtSupport.issue(encoder, "42", "USER", 3600);
    mvc.perform(get("/api/hotels").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(get("/api/hotels").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
    var claims =
        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
            .issuer(JwtSupport.ISSUER)
            .audience(List.of("hotel-api"))
            .subject("42")
            .issuedAt(Instant.now().minusSeconds(7200))
            .expiresAt(Instant.now().minusSeconds(120))
            .claim("roles", List.of("USER"))
            .build();
    String expired =
        encoder
            .encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                    org.springframework.security.oauth2.jwt.JwsHeader.with(
                            org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build(),
                    claims))
            .getTokenValue();
    mvc.perform(get("/api/hotels").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminCrudAndValidation() throws Exception {
    var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    String body =
        mvc.perform(
                post("/api/hotels")
                    .with(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"New Hotel\",\"address\":\"Street\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();
    mvc.perform(
            put("/api/hotels/" + id)
                .with(admin)
                .contentType("application/json")
                .content("{\"name\":\"Updated\",\"address\":\"Street\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated"));
    mvc.perform(delete("/api/hotels/" + id).with(admin)).andExpect(status().isNoContent());
    mvc.perform(get("/api/hotels/" + id).with(admin)).andExpect(status().isNotFound());
    mvc.perform(
            post("/api/hotels")
                .with(admin)
                .contentType("application/json")
                .content("{\"name\":\"\",\"address\":\"Street\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/hotels/" + hotelId).with(admin)).andExpect(status().isConflict());
  }

  @Test
  void recommendationsSortByCountThenIdAndExcludeRepairsAndOverlaps() {
    availability.confirm(
        roomId, new SlotRequest(UUID.randomUUID(), start.plusDays(10), end.plusDays(10)));
    assertThat(catalog.free(hotelId, start, end, true))
        .extracting(RoomView::id)
        .containsExactly(secondRoomId, roomId);
    availability.confirm(secondRoomId, new SlotRequest(UUID.randomUUID(), start, end));
    assertThat(catalog.free(hotelId, start, end, true))
        .extracting(RoomView::id)
        .containsExactly(roomId);
  }

  @Test
  void adjacentStaysDoNotOverlapAndInvalidDatesAreRejected() {
    availability.confirm(roomId, new SlotRequest(UUID.randomUUID(), start, end));
    availability.confirm(roomId, new SlotRequest(UUID.randomUUID(), end, end.plusDays(1)));
    assertThatThrownBy(
            () ->
                availability.confirm(
                    roomId, new SlotRequest(UUID.randomUUID(), start.plusDays(1), end.plusDays(1))))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> catalog.free(null, end, start, true)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> catalog.free(null, start.minusDays(30), end, true))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void repeatedConfirmAndReleaseAreIdempotentAndLateConfirmCannotResurrect() {
    UUID id = UUID.randomUUID();
    SlotRequest request = new SlotRequest(id, start, end);
    availability.confirm(roomId, request);
    availability.confirm(roomId, request);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
    availability.release(roomId, id);
    availability.release(roomId, id);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isZero();
    assertThatThrownBy(() -> availability.confirm(roomId, request))
        .isInstanceOf(ApiException.class);
    UUID late = UUID.randomUUID();
    availability.release(roomId, late);
    assertThatThrownBy(() -> availability.confirm(roomId, new SlotRequest(late, start, end)))
        .isInstanceOf(ApiException.class);
    assertThat(reservations.count()).isEqualTo(2);
  }

  @Test
  void parallelOverlappingReservationsHaveExactlyOneWinner() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(12);
    CountDownLatch gate = new CountDownLatch(1);
    AtomicInteger accepted = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 24; i++)
        futures.add(
            executor.submit(
                () -> {
                  try {
                    gate.await();
                    availability.confirm(roomId, new SlotRequest(UUID.randomUUID(), start, end));
                    accepted.incrementAndGet();
                  } catch (ApiException e) {
                    assertThat(e.status().value()).isEqualTo(409);
                    conflicts.incrementAndGet();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                  }
                }));
      gate.countDown();
      for (Future<?> f : futures) f.get(20, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
    assertThat(accepted.get()).isEqualTo(1);
    assertThat(conflicts.get()).isEqualTo(23);
    assertThat(reservations.count()).isEqualTo(1);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
  }

  @Test
  void parallelDuplicateConfirmIncrementsCounterOnce() throws Exception {
    var request = new SlotRequest(UUID.randomUUID(), start, end);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      var jobs = new ArrayList<Callable<Void>>();
      for (int i = 0; i < 20; i++)
        jobs.add(
            () -> {
              availability.confirm(roomId, request);
              return null;
            });
      for (var f : pool.invokeAll(jobs)) f.get();
    } finally {
      pool.shutdownNow();
    }
    assertThat(reservations.count()).isEqualTo(1);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
  }

  @Test
  void statisticsClipDatesAndCancellationRestoresAvailability() {
    UUID id = UUID.randomUUID();
    availability.confirm(roomId, new SlotRequest(id, start, end));
    var stats = statistics.statistics(hotelId, start.plusDays(1), end.plusDays(1), "occupancy");
    assertThat(stats.occupiedNights()).isEqualTo(2);
    assertThat(stats.rooms().get(0).roomId()).isEqualTo(roomId);
    assertThat(stats.rooms().get(0).occupancyPercent()).isCloseTo(66.666, within(0.01));
    availability.release(roomId, id);
    assertThat(statistics.statistics(hotelId, start, end, "id").occupiedNights()).isZero();
  }

  @Test
  void internalRoutesRequireInternalRoleAndValidatePayload() throws Exception {
    String payload =
        "{\"bookingId\":\""
            + UUID.randomUUID()
            + "\",\"startDate\":\""
            + start
            + "\",\"endDate\":\""
            + end
            + "\"}";
    mvc.perform(
            post("/api/rooms/" + roomId + "/confirm-availability")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/rooms/" + roomId + "/confirm-availability")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INTERNAL")))
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/rooms")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .param("startDate", end.toString())
                .param("endDate", start.toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void roomCrudDuplicateAndHistoricalDeleteProtection() throws Exception {
    var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    mvc.perform(
            post("/api/rooms")
                .with(admin)
                .contentType("application/json")
                .content("{\"hotelId\":" + hotelId + ",\"number\":\"101\",\"available\":true}"))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/rooms/" + secondRoomId)
                .with(admin)
                .contentType("application/json")
                .content("{\"number\":\"202\",\"available\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false));
    mvc.perform(delete("/api/rooms/" + secondRoomId).with(admin)).andExpect(status().isNoContent());
    availability.confirm(roomId, new SlotRequest(UUID.randomUUID(), start, end));
    mvc.perform(delete("/api/rooms/" + roomId).with(admin)).andExpect(status().isConflict());
  }
}
