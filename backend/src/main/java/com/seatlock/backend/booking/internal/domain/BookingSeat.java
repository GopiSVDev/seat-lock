package com.seatlock.backend.booking.internal.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_seat", schema = "booking",
        uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "show_seat_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false)
    private Long showSeatId;

    @Column(nullable = false)
    private String seatLabel;

    public BookingSeat(Booking booking, Long showSeatId, String seatLabel) {
        this.booking = booking;
        this.showSeatId = showSeatId;
        this.seatLabel = seatLabel;
    }
}
