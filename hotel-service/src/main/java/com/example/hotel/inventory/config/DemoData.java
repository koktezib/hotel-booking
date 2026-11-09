package com.example.hotel.inventory.config;

import com.example.hotel.inventory.entity.*;
import com.example.hotel.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoData implements ApplicationRunner {
  private final HotelRepository hotels;
  private final RoomRepository rooms;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Hotel a = hotels.save(new Hotel("Nevsky Hotel", "Saint Petersburg, Nevsky 10"));
    Hotel b = hotels.save(new Hotel("Volga Hotel", "Kazan, Central 20"));
    for (int i = 1; i <= 4; i++) rooms.save(new Room(a, "10" + i, true));
    rooms.save(new Room(a, "105-repair", false));
    for (int i = 1; i <= 3; i++) rooms.save(new Room(b, "20" + i, true));
  }
}
