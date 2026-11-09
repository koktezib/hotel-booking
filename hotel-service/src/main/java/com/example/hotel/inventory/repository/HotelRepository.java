package com.example.hotel.inventory.repository;

import com.example.hotel.inventory.entity.Hotel;
import org.springframework.data.jpa.repository.*;

public interface HotelRepository
    extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {}
