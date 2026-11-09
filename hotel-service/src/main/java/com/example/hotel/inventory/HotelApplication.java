package com.example.hotel.inventory;

import com.example.hotel.common.*;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({ServletSecurity.class, ApiErrors.class})
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class HotelApplication {
  public static void main(String[] args) {
    SpringApplication.run(HotelApplication.class, args);
  }
}
