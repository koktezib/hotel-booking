package com.example.hotel.inventory.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "bookings",
    indexes =
        @Index(name = "idx_slot_overlap", columnList = "room_id,released,start_date,end_date"))
@Getter
@Setter
@NoArgsConstructor
public class Reservation {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id")
  private Room room;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(nullable = false)
  private boolean released;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  public Reservation(UUID id, Room room, LocalDate start, LocalDate end, boolean released) {
    this.id = id;
    this.room = room;
    this.startDate = start;
    this.endDate = end;
    this.released = released;
  }
}
