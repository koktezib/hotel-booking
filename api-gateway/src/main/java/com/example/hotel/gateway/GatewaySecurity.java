package com.example.hotel.gateway;

import com.example.hotel.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
@Import(JwtSupport.class)
public class GatewaySecurity {
  @Bean
  ReactiveJwtDecoder reactiveJwtDecoder(JwtDecoder decoder) {
    return token -> Mono.fromCallable(() -> decoder.decode(token));
  }

  @Bean
  SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http, JwtAuthenticationConverter converter, ObjectMapper mapper) {
    return http.csrf(c -> c.disable())
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(
            a ->
                a.pathMatchers("/api/rooms/*/release", "/api/rooms/*/confirm-availability")
                    .denyAll()
                    .pathMatchers(
                        "/user/register",
                        "/user/auth",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/actuator/health")
                    .permitAll()
                    .anyExchange()
                    .hasAnyRole("USER", "ADMIN"))
        .oauth2ResourceServer(
            o ->
                o.jwt(
                        j ->
                            j.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(converter)))
                    .authenticationEntryPoint((x, e) -> error(x, 401, mapper))
                    .accessDeniedHandler((x, e) -> error(x, 403, mapper)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((x, t) -> error(x, 401, mapper))
                    .accessDeniedHandler((x, t) -> error(x, 403, mapper)))
        .build();
  }

  static Mono<Void> error(ServerWebExchange exchange, int status, ObjectMapper mapper) {
    var response = exchange.getResponse();
    response.setStatusCode(HttpStatus.valueOf(status));
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    if (status == 401) response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    String message = HttpStatus.valueOf(status).getReasonPhrase();
    try {
      byte[] bytes =
          mapper.writeValueAsBytes(
              new ErrorDto(
                  Instant.now(),
                  status,
                  message,
                  message,
                  exchange.getRequest().getPath().value(),
                  exchange.getAttribute("traceId")));
      return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    } catch (Exception e) {
      return response.setComplete();
    }
  }
}
