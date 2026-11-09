package com.seatlock.backend.catalogue.internal.show;

import com.seatlock.backend.catalogue.internal.screen.SeatType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Fixed once the show exists: holds and bookings copy it
@Entity
@Table(name = "show_price", schema = "catalogue",
        uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id")
    private Show show;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatType;

    // Paise
    @Column(nullable = false)
    private long price;

    public ShowPrice(Show show, SeatType seatType, long price) {
        this.show = show;
        this.seatType = seatType;
        this.price = price;
    }
}
