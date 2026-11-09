package com.example.hotel.booking.service;

import com.example.hotel.booking.api.UserDtos.*;
import com.example.hotel.booking.entity.*;
import com.example.hotel.booking.entity.UserAccount.Role;
import com.example.hotel.booking.repository.*;
import com.example.hotel.common.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository users;
  private final BookingRepository bookings;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;

  @Transactional
  public Token register(Credentials in) {
    return token(create(in.username(), in.password(), Role.USER));
  }

  @Transactional(readOnly = true)
  public Token login(Credentials in) {
    UserAccount u =
        users
            .findByUsername(in.username())
            .filter(UserAccount::isEnabled)
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
    if (!passwords.matches(in.password(), u.getPassword()))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    return token(u);
  }

  @Transactional
  public UserView create(CreateUser in) {
    return view(create(in.username(), in.password(), in.role()));
  }

  @Transactional
  public UserView update(Long id, UpdateUser in) {
    UserAccount u = users.lock(id).orElseThrow(() -> ApiException.notFound("User not found"));
    if (in.username() != null && !in.username().equals(u.getUsername())) {
      if (users.existsByUsername(in.username()))
        throw ApiException.conflict("Username already exists");
      u.setUsername(in.username());
    }
    if (in.password() != null) {
      validatePasswordBytes(in.password());
      u.setPassword(passwords.encode(in.password()));
    }
    if (in.role() != null) u.setRole(in.role());
    try {
      users.flush();
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw ApiException.conflict("Username already exists");
    }
    return view(u);
  }

  @Transactional
  public void delete(Long id) {
    UserAccount u = users.lock(id).orElseThrow(() -> ApiException.notFound("User not found"));
    if (bookings.existsByUserIdAndStatusNot(id, Booking.Status.CANCELLED))
      throw ApiException.conflict("Cancel user's bookings before deleting account");
    u.setEnabled(false);
  }

  @Transactional(readOnly = true)
  public UserView get(Long id) {
    return view(users.findById(id).orElseThrow(() -> ApiException.notFound("User not found")));
  }

  @Transactional(readOnly = true)
  public PageDto<UserView> list(int page, int size) {
    PageDto.validate(page, size);
    var p = users.findAll(PageRequest.of(page, size, Sort.by("id")));
    return new PageDto<>(
        p.map(this::view).getContent(), page, size, p.getTotalElements(), p.getTotalPages());
  }

  private UserAccount create(String username, String password, Role role) {
    validatePasswordBytes(password);
    if (users.existsByUsername(username)) throw ApiException.conflict("Username already exists");
    try {
      return users.saveAndFlush(new UserAccount(username, passwords.encode(password), role));
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw ApiException.conflict("Username already exists");
    }
  }

  private void validatePasswordBytes(String password) {
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw ApiException.badRequest("Password must fit into 72 UTF-8 bytes");
  }

  private Token token(UserAccount u) {
    return new Token(
        JwtSupport.issue(encoder, u.getId().toString(), u.getRole().name(), 3600),
        "Bearer",
        3600,
        view(u));
  }

  private UserView view(UserAccount u) {
    return new UserView(u.getId(), u.getUsername(), u.getRole(), u.isEnabled());
  }
}
