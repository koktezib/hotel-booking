package com.example.hotel.booking.security;

import com.example.hotel.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("accountAccess")
@RequiredArgsConstructor
public class AccountAccess {
  private final UserRepository users;

  public boolean active(Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken token)) return false;
    try {
      return users
          .findById(Long.valueOf(token.getName()))
          .filter(
              u ->
                  u.isEnabled()
                      && token.getAuthorities().stream()
                          .anyMatch(a -> a.getAuthority().equals("ROLE_" + u.getRole().name())))
          .isPresent();
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
