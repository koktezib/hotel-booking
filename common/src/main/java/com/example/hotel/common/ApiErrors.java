package com.example.hotel.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorDto> api(ApiException e, HttpServletRequest r) {
    return response(e.status(), e.getMessage(), r);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ResponseEntity<ErrorDto> validation(Exception e, HttpServletRequest r) {
    return response(
        HttpStatus.BAD_REQUEST, "Invalid request: check fields, dates and parameter types", r);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorDto> denied(Exception e, HttpServletRequest r) {
    return response(HttpStatus.FORBIDDEN, "Forbidden", r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorDto> unexpected(Exception e, HttpServletRequest r) {
    if (e instanceof org.springframework.web.ErrorResponse error) {
      HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
      if (status != null) {
        return ResponseEntity.status(status)
            .headers(error.getHeaders())
            .body(response(status, status.getReasonPhrase(), r).getBody());
      }
    }
    log.error("event=unexpected_error path={}", r.getRequestURI(), e);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", r);
  }

  private ResponseEntity<ErrorDto> response(
      HttpStatus status, String message, HttpServletRequest r) {
    return ResponseEntity.status(status)
        .body(
            new ErrorDto(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                r.getRequestURI(),
                (String) r.getAttribute("traceId")));
  }
}
