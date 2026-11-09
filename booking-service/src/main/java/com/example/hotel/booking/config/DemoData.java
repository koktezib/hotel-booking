package com.example.hotel.booking.config;

import com.example.hotel.booking.entity.*;
import com.example.hotel.booking.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoData implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwords;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    users.save(new UserAccount("admin", passwords.encode("Admin123!"), UserAccount.Role.ADMIN));
    users.save(new UserAccount("alice", passwords.encode("Alice123!"), UserAccount.Role.USER));
    users.save(new UserAccount("bob", passwords.encode("Bob12345!"), UserAccount.Role.USER));
  }
}
