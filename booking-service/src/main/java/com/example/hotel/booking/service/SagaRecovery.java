package com.example.hotel.booking.service;

import com.example.hotel.booking.entity.Booking.Status;
import java.time.*;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SagaRecovery {
  private static final Logger log = LoggerFactory.getLogger(SagaRecovery.class);
  private final BookingTransactions transactions;
  private final BookingSaga saga;
  private final long staleSeconds;

  public SagaRecovery(
      BookingTransactions transactions,
      BookingSaga saga,
      @Value("${saga.stale-seconds:120}") long staleSeconds) {
    this.transactions = transactions;
    this.saga = saga;
    this.staleSeconds = staleSeconds;
  }

  @Scheduled(
      fixedDelayString = "${saga.recovery-delay-ms:5000}",
      initialDelayString = "${saga.recovery-delay-ms:5000}")
  public void recover() {
    for (UUID id : transactions.recoverable(Instant.now().minusSeconds(staleSeconds))) {
      try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", transactions.trace(id))) {
        var b = transactions.get(id);
        if (b.status() == Status.PENDING)
          b = transactions.expire(id, Instant.now().minusSeconds(staleSeconds));
        saga.compensate(b);
      } catch (RuntimeException e) {
        log.error("event=recovery_failed bookingId={}", id, e);
      }
    }
  }
}
