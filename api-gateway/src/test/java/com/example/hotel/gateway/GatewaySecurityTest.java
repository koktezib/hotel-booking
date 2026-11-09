package com.example.hotel.gateway;

import com.example.hotel.common.JwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
@AutoConfigureWebTestClient
class GatewaySecurityTest {
  @Autowired WebTestClient web;
  @Autowired JwtEncoder encoder;

  @Test
  void protectedRoutesRequireJwt() {
    web.get()
        .uri("/api/hotels")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .exists("X-Trace-Id")
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401);
  }

  @Test
  void invalidJwtIsRejected() {
    web.get()
        .uri("/api/hotels")
        .headers(h -> h.setBearerAuth("invalid"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void internalRoutesAreNeverPublished() {
    String token = JwtSupport.issue(encoder, "1", "ADMIN", 3600);
    for (String operation : new String[] {"release", "confirm-availability"})
      web.post()
          .uri("/api/rooms/1/" + operation)
          .headers(h -> h.setBearerAuth(token))
          .exchange()
          .expectStatus()
          .isForbidden();
  }

  @Test
  void serviceTokenCannotUsePublicGateway() {
    web.get()
        .uri("/api/hotels")
        .headers(h -> h.setBearerAuth(JwtSupport.issue(encoder, "booking-service", "INTERNAL", 60)))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void healthIsPublicAndTraceIsPreserved() {
    web.get()
        .uri("/actuator/health")
        .header("X-Trace-Id", "test-trace-123")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Trace-Id", "test-trace-123");
  }
}
