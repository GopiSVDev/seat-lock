package com.seatlock.backend.booking.internal.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "booking", schema = "booking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    // Read-only mapping: rows are inserted by BookingWriteRepository
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long showId;

    @Embedded
    private ShowSnapshot show;

    @Column(nullable = false)
    private String customerId;

    // Name at booking time, from the token
    @Column(nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    // Paise: sum of the seats' prices at booking time
    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant cancelledAt;

    // One booking per hold
    private String holdId;

    // The payment service's id (another database, so no foreign key)
    private Long paymentId;

    // Deadline to pay; the hold lives until then
    private Instant paymentExpiresAt;
}
