package com.seatlock.backend.booking.internal.repository;

import com.seatlock.backend.booking.internal.domain.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Ownership in the query: another customer's booking is simply not found
    Optional<Booking> findByIdAndCustomerId(Long id, String customerId);

    Page<Booking> findByCustomerId(String customerId, Pageable pageable);
}
