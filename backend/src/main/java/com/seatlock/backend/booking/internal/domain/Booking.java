package com.seatlock.backend.booking.internal.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "booking", schema = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long showId;

    @Column(nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getShowId() {
        return showId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
