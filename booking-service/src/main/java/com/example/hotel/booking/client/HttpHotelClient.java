package com.example.hotel.booking.client;

import com.example.hotel.common.*;
import com.example.hotel.common.Contracts.*;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.*;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class HttpHotelClient implements HotelPort {
  private static final Logger log = LoggerFactory.getLogger(HttpHotelClient.class);
  private final RestClient http;
  private final LoadBalancerClient loadBalancer;
  private final JwtEncoder encoder;
  private final String baseUrl;
  private final Retry retry;
  private final CircuitBreaker operations;
  private final CircuitBreaker compensations;

  public HttpHotelClient(
      LoadBalancerClient loadBalancer,
      JwtEncoder encoder,
      @Value("${hotel.base-url:}") String baseUrl,
      @Value("${hotel.timeout-ms:1500}") long timeout,
      @Value("${hotel.retry-attempts:3}") int attempts,
      @Value("${hotel.retry-delay-ms:100}") long delay,
      @Value("${hotel.circuit-open-ms:5000}") long openMs) {
    this.loadBalancer = loadBalancer;
    this.encoder = encoder;
    this.baseUrl = baseUrl;
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).build());
    factory.setReadTimeout(Duration.ofMillis(timeout));
    http = RestClient.builder().requestFactory(factory).build();
    retry =
        Retry.of(
            "hotel",
            RetryConfig.custom()
                .maxAttempts(attempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(delay, 2))
                .retryOnException(this::transientError)
                .build());
    retry
        .getEventPublisher()
        .onRetry(
            e ->
                log.warn(
                    "event=hotel_retry attempt={} reason={}",
                    e.getNumberOfRetryAttempts(),
                    e.getLastThrowable().getClass().getSimpleName()));
    var config =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(openMs))
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordException(this::transientError)
            .ignoreExceptions(HttpClientErrorException.class)
            .build();
    operations = CircuitBreaker.of("hotel-operations", config);
    compensations = CircuitBreaker.of("hotel-compensations", config);
    operations
        .getEventPublisher()
        .onStateTransition(
            e ->
                log.warn(
                    "event=circuit_transition circuit=operations transition={}",
                    e.getStateTransition()));
    compensations
        .getEventPublisher()
        .onStateTransition(
            e ->
                log.warn(
                    "event=circuit_transition circuit=compensations transition={}",
                    e.getStateTransition()));
  }

  @Override
  public List<RoomView> recommend(Long hotelId, LocalDate start, LocalDate end) {
    return invoke(
        operations,
        () -> {
          var uri =
              UriComponentsBuilder.fromUriString(base() + "/api/rooms/recommend")
                  .queryParam("startDate", start)
                  .queryParam("endDate", end);
          if (hotelId != null) uri.queryParam("hotelId", hotelId);
          RoomView[] rooms =
              http.get()
                  .uri(uri.build().toUri())
                  .headers(this::headers)
                  .retrieve()
                  .body(RoomView[].class);
          return rooms == null ? List.of() : Arrays.asList(rooms);
        });
  }

  @Override
  public void confirm(Long roomId, SlotRequest request) {
    invoke(
        operations,
        () -> {
          http.post()
              .uri(base() + "/api/rooms/" + roomId + "/confirm-availability")
              .headers(this::headers)
              .body(request)
              .retrieve()
              .toBodilessEntity();
          return null;
        });
  }

  @Override
  public void release(Long roomId, UUID bookingId) {
    invoke(
        compensations,
        () -> {
          http.post()
              .uri(base() + "/api/rooms/" + roomId + "/release")
              .headers(this::headers)
              .body(new ReleaseRequest(bookingId))
              .retrieve()
              .toBodilessEntity();
          return null;
        });
  }

  private <T> T invoke(CircuitBreaker circuit, Supplier<T> action) {
    try {
      return Retry.decorateSupplier(retry, () -> circuit.executeSupplier(action)).get();
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == 409)
        throw ApiException.conflict("Hotel rejected unavailable room or replay");
      if (e.getStatusCode().value() == 404) throw ApiException.notFound("Room not found");
      throw new ApiException(HttpStatus.BAD_GATEWAY, "Hotel rejected internal request");
    } catch (ResourceAccessException e) {
      throw new ApiException(
          HttpStatus.GATEWAY_TIMEOUT, "Hotel request timed out or connection failed");
    } catch (HttpServerErrorException | CallNotPermittedException e) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel temporarily unavailable");
    }
  }

  private boolean transientError(Throwable e) {
    return e instanceof ResourceAccessException || e instanceof HttpServerErrorException;
  }

  private String base() {
    if (!baseUrl.isBlank()) return baseUrl;
    var instance = loadBalancer.choose("hotel-service");
    if (instance == null)
      throw new ResourceAccessException("No hotel-service instances discovered");
    return instance.getUri().toString();
  }

  private void headers(HttpHeaders h) {
    h.setBearerAuth(JwtSupport.issue(encoder, "booking-service", "INTERNAL", 60));
    String trace = MDC.get("traceId");
    if (trace != null) h.set("X-Trace-Id", trace);
  }
}
