package com.example.hotel.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@Import(JwtSupport.class)
public class ServletSecurity {
  @Bean
  FilterRegistrationBean<TraceFilter> traceFilter() {
    FilterRegistrationBean<TraceFilter> bean = new FilterRegistrationBean<>(new TraceFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
  }

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http, JwtAuthenticationConverter converter, ObjectMapper mapper)
      throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/user/register",
                        "/user/auth",
                        "/api/auth/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint((q, r, e) -> error(mapper, q, r, 401, "Unauthorized"))
                    .accessDeniedHandler((q, r, e) -> error(mapper, q, r, 403, "Forbidden")))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((q, r, x) -> error(mapper, q, r, 401, "Unauthorized"))
                    .accessDeniedHandler((q, r, x) -> error(mapper, q, r, 403, "Forbidden")));
    return http.build();
  }

  private void error(
      ObjectMapper mapper,
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    if (status == 401) response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    mapper.writeValue(
        response.getOutputStream(),
        new ErrorDto(
            Instant.now(),
            status,
            message,
            message,
            request.getRequestURI(),
            (String) request.getAttribute("traceId")));
  }
}
