package com.example.hotel.booking.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "bookings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}),
    indexes = {
      @Index(name = "idx_booking_owner", columnList = "user_id,created_at"),
      @Index(name = "idx_booking_recovery", columnList = "status,release_pending,created_at")
    })
@Getter
@Setter
@NoArgsConstructor
public class Booking {
  public enum Status {
    PENDING,
    CONFIRMED,
    CANCELLED
  }

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private UserAccount user;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "room_id")
  private Long roomId;

  private Long hotelId;

  @Column(nullable = false)
  private boolean autoSelect;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "release_pending", nullable = false)
  private boolean releasePending;

  private Integer failureStatus;

  @Column(length = 500)
  private String failureReason;

  @Column(nullable = false, length = 64)
  private String traceId;

  @Version private long version;
}
