package com.seatlock.backend.booking.internal.web.dto;

import com.seatlock.backend.booking.internal.domain.Booking;
import com.seatlock.backend.booking.internal.domain.BookingSeat;
import com.seatlock.backend.booking.internal.domain.BookingStatus;
import com.seatlock.backend.booking.internal.domain.ShowSnapshot;

import java.time.Instant;
import java.util.List;

public record BookingResponse(Long id, String customerName, BookingStatus status, Instant createdAt,
        Instant cancelledAt, long totalAmount,
        // Set while PENDING_PAYMENT: the deadline to pay (for a countdown)
        Instant paymentExpiresAt,
        // Only in the checkout response: what the browser needs to open Razorpay
        // Checkout
        Checkout checkout, Show show, List<Seat> seats) {

    // amount in paise; keyId is Razorpay's public key id
    public record Checkout(String razorpayOrderId, String keyId, long amount, String currency) {
    }

    public BookingResponse withCheckout(Checkout checkout) {
        return new BookingResponse(id, customerName, status, createdAt, cancelledAt, totalAmount, paymentExpiresAt,
                checkout, show, seats);
    }

    // Amounts in paise, as they were when booked
    public record Seat(Long showSeatId, String label, long price) {
    }

    // As it was when booked
    public record Show(Long id, Instant startTime, String language, String format, Movie movie, Theatre theatre,
            String screenName) {

        public static Show from(Long showId, ShowSnapshot show) {
            return new Show(showId, show.getStartTime(), show.getLanguage(), show.getFormat(),
                    new Movie(show.getMovieId(), show.getMovieTitle(), show.getMoviePosterUrl(),
                            show.getMovieCertification()),
                    new Theatre(show.getTheatreId(), show.getTheatreName()), show.getScreenName());
        }
    }

    public record Movie(Long id, String title, String posterUrl, String certification) {
    }

    public record Theatre(Long id, String name) {
    }

    public static BookingResponse from(Booking booking, List<BookingSeat> bookingSeats) {
        List<Seat> seats = bookingSeats.stream()
                .map(seat -> new Seat(seat.getShowSeatId(), seat.getSeatLabel(), seat.getPrice())).toList();

        return new BookingResponse(booking.getId(), booking.getCustomerName(), booking.getStatus(),
                booking.getCreatedAt(), booking.getCancelledAt(), booking.getTotalAmount(),
                booking.getStatus() == BookingStatus.PENDING_PAYMENT ? booking.getPaymentExpiresAt() : null, null,
                Show.from(booking.getShowId(), booking.getShow()), seats);
    }
}
