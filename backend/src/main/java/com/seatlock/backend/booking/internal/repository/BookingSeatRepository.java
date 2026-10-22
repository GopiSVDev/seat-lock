package com.seatlock.backend.booking.internal.repository;

import com.seatlock.backend.booking.internal.domain.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    List<BookingSeat> findByBookingIdOrderByIdAsc(Long bookingId);
}
