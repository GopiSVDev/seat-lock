package com.seatlock.backend.booking.internal.web;

import com.seatlock.backend.booking.internal.domain.BookingPeriod;
import com.seatlock.backend.booking.internal.service.BookingService;
import com.seatlock.backend.booking.internal.web.dto.BookingResponse;
import com.seatlock.backend.booking.internal.web.dto.CreateBookingRequest;
import com.seatlock.backend.booking.internal.web.dto.PaymentConfirmationRequest;
import com.seatlock.backend.shared.security.CurrentUser;
import com.seatlock.backend.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static com.seatlock.backend.shared.web.ApiVersionConfig.API_V1;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // Checkout: 201 with a new unpaid booking, 200 when the hold already has one
    @PostMapping
    public ResponseEntity<BookingResponse> checkout(@Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        BookingService.Checkout checkout = bookingService.checkout(request, CurrentUser.from(jwt));
        BookingResponse response = checkout.booking();
        return checkout.isNew()
                ? ResponseEntity.created(URI.create(API_V1 + "/bookings/" + response.id())).body(response)
                : ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/payment")
    public BookingResponse pay(@PathVariable Long id, @Valid @RequestBody PaymentConfirmationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return bookingService.pay(id, request, CurrentUser.from(jwt));
    }

    @GetMapping
    public PageResponse<BookingResponse> findMine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "UPCOMING") BookingPeriod period,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return bookingService.findMine(CurrentUser.from(jwt), period, page, size);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.cancel(id, CurrentUser.from(jwt));
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getById(id, CurrentUser.from(jwt));
    }
}
