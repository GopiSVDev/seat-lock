package com.seatlock.backend.booking.internal.web;

import com.seatlock.backend.booking.internal.service.BookingService;
import com.seatlock.backend.booking.internal.web.dto.PaidRequest;
import com.seatlock.backend.shared.web.InternalEndpoint;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Called by the payment service only (role BOOKING_CALLBACK, see SecurityConfig); idempotent
@InternalEndpoint
@RestController
@RequestMapping("/internal/bookings")
@RequiredArgsConstructor
public class InternalBookingController {

    private final BookingService bookingService;

    @PostMapping("/{id}/paid")
    public ResponseEntity<Void> paid(@PathVariable Long id, @Valid @RequestBody PaidRequest request) {
        bookingService.paid(id, request.paymentId());
        return ResponseEntity.noContent().build();
    }
}
