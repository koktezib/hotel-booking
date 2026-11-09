package com.example.hotel.booking.api;

import com.example.hotel.booking.api.UserDtos.*;
import com.example.hotel.booking.service.UserService;
import com.example.hotel.common.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and @accountAccess.active(authentication)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
  private final UserService users;

  @PostMapping({"/user", "/api/users"})
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public UserView create(@Valid @RequestBody CreateUser in) {
    return users.create(in);
  }

  @GetMapping("/api/users")
  public PageDto<UserView> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return users.list(page, size);
  }

  @GetMapping("/api/users/{id}")
  public UserView get(@PathVariable Long id) {
    return users.get(id);
  }

  @PatchMapping("/api/users/{id}")
  public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUser in) {
    return users.update(id, in);
  }

  @PatchMapping("/user")
  public UserView updateLegacy(@RequestParam Long id, @Valid @RequestBody UpdateUser in) {
    return users.update(id, in);
  }

  @DeleteMapping("/api/users/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    users.delete(id);
  }

  @DeleteMapping("/user")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteLegacy(@RequestParam Long id) {
    users.delete(id);
  }
}
