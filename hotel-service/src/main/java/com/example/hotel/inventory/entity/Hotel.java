package com.example.hotel.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hotels")
@Getter
@Setter
@NoArgsConstructor
public class Hotel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 250)
  private String address;

  public Hotel(String name, String address) {
    this.name = name;
    this.address = address;
  }
}
