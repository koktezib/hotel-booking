package com.example.hotel.booking.api;

import com.example.hotel.booking.api.UserDtos.*;
import com.example.hotel.booking.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {
  private final UserService users;

  @PostMapping({"/user/register", "/api/auth/register"})
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Token register(@Valid @RequestBody Credentials in) {
    return users.register(in);
  }

  @PostMapping({"/user/auth", "/api/auth/login"})
  public Token login(@Valid @RequestBody Credentials in) {
    return users.login(in);
  }
}
