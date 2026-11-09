package com.example.hotel.booking.api;

import com.example.hotel.booking.entity.UserAccount.Role;
import jakarta.validation.constraints.*;

public final class UserDtos {
  private UserDtos() {}

  public record Credentials(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") String username,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  public record CreateUser(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") String username,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotNull Role role) {}

  public record UpdateUser(
      @Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") String username,
      @Size(min = 8, max = 72) String password,
      Role role) {}

  public record UserView(Long id, String username, Role role, boolean enabled) {}

  public record Token(String accessToken, String tokenType, long expiresIn, UserView user) {}
}
