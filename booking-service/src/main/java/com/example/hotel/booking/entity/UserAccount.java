package com.example.hotel.booking.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {
  public enum Role {
    USER,
    ADMIN
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String username;

  @Column(nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(nullable = false)
  private boolean enabled = true;

  public UserAccount(String username, String password, Role role) {
    this.username = username;
    this.password = password;
    this.role = role;
  }
}
