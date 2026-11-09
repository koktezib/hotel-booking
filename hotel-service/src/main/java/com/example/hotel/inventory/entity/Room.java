package com.example.hotel.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "rooms",
    uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "number"}),
    indexes = @Index(name = "idx_room_rank", columnList = "available,times_booked,id"))
@Getter
@Setter
@NoArgsConstructor
public class Room {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "hotel_id")
  private Hotel hotel;

  @Column(nullable = false, length = 30)
  private String number;

  @Column(nullable = false)
  private boolean available = true;

  @Column(name = "times_booked", nullable = false)
  private long timesBooked;

  @Version private long version;

  public Room(Hotel hotel, String number, boolean available) {
    this.hotel = hotel;
    this.number = number;
    this.available = available;
  }
}
