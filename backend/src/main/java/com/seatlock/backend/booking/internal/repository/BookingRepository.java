package com.seatlock.backend.booking.internal.repository;

import com.seatlock.backend.booking.internal.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}
