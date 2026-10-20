package com.seatlock.backend.booking.internal.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "booking_seat", schema = "booking",
        uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "show_seat_id"}))
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

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public Long getShowSeatId() {
        return showSeatId;
    }

    public String getSeatLabel() {
        return seatLabel;
    }
}
