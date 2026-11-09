package com.example.hotel.booking.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.hotel.common.*;
import com.example.hotel.common.Contracts.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class HttpHotelClientTest {
  HttpServer server;
  ExecutorService executor;
  HttpHotelClient client;
  AtomicInteger calls = new AtomicInteger(), releaseCalls = new AtomicInteger();
  AtomicInteger response = new AtomicInteger(200);
  AtomicLong delay = new AtomicLong();
  AtomicReference<String> token = new AtomicReference<>();

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/api/rooms",
        exchange -> {
          boolean release = exchange.getRequestURI().getPath().endsWith("/release");
          if (release) releaseCalls.incrementAndGet();
          else calls.incrementAndGet();
          token.set(exchange.getRequestHeaders().getFirst("Authorization"));
          try {
            if (!release && delay.get() > 0) Thread.sleep(delay.get());
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(release ? 200 : response.get(), body.length);
            exchange.getResponseBody().write(body);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (java.io.IOException ignored) {

          } finally {
            exchange.close();
          }
        });
    server.start();
    var encoder =
        new NimbusJwtEncoder(
            new ImmutableSecret<>(
                new SecretKeySpec(
                    "test-signing-secret-with-32-bytes-minimum".getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256")));
    client =
        new HttpHotelClient(
            mock(LoadBalancerClient.class),
            encoder,
            "http://127.0.0.1:" + server.getAddress().getPort(),
            200,
            3,
            10,
            1000);
  }

  @AfterEach
  void shutdown() {
    server.stop(0);
    executor.shutdownNow();
  }

  private SlotRequest request() {
    return new SlotRequest(
        UUID.randomUUID(), LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
  }

  @Test
  void successSendsInternalJwt() {
    client.confirm(1L, request());
    assertThat(calls.get()).isEqualTo(1);
    assertThat(token.get()).startsWith("Bearer ");
  }

  @Test
  void serverErrorsAreRetriedWithBoundedAttempts() {
    response.set(503);
    assertThatThrownBy(() -> client.confirm(1L, request()))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).status().value()).isEqualTo(503));
    assertThat(calls.get()).isEqualTo(3);
  }

  @Test
  void conflictsAreNotRetried() {
    response.set(409);
    assertThatThrownBy(() -> client.confirm(1L, request())).isInstanceOf(ApiException.class);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void realHttpTimeoutIsBoundedAndRetried() {
    delay.set(800);
    long before = System.nanoTime();
    assertThatThrownBy(() -> client.confirm(1L, request()))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).status().value()).isEqualTo(504));
    assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(3));
    assertThat(calls.get()).isEqualTo(3);
  }

  @Test
  void circuitOpensAndCompensationUsesIndependentCircuit() {
    response.set(503);
    for (int i = 0; i < 2; i++)
      assertThatThrownBy(() -> client.confirm(1L, request())).isInstanceOf(ApiException.class);
    int count = calls.get();
    assertThatThrownBy(() -> client.confirm(1L, request())).isInstanceOf(ApiException.class);
    assertThat(calls.get()).isEqualTo(count);
    client.release(1L, UUID.randomUUID());
    assertThat(releaseCalls.get()).isEqualTo(1);
  }
}
