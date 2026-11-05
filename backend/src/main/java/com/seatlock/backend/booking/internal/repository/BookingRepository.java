package com.seatlock.backend.booking.internal.repository;

import com.seatlock.backend.booking.internal.domain.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Ownership in the query: another customer's booking is simply not found
    Optional<Booking> findByIdAndCustomerId(Long id, String customerId);

    // Both use ix_booking_customer_show_start; the Pageable's sort sets the direction
    @Query("select b from Booking b where b.customerId = :customerId and b.show.startTime > :now")
    Page<Booking> findUpcoming(String customerId, Instant now, Pageable pageable);

    @Query("select b from Booking b where b.customerId = :customerId and b.show.startTime <= :now")
    Page<Booking> findPast(String customerId, Instant now, Pageable pageable);
}
