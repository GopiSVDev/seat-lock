package com.seatlock.backend.inventory.internal.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "show_seat", schema = "inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long showId;

    @Column(nullable = false)
    private Long seatId;

    @Column(nullable = false)
    private String rowLabel;

    @Column(nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShowSeatStatus status;

    public ShowSeat(Long showId, Long seatId, String rowLabel, int seatNumber, SeatType seatType) {
        this.showId = showId;
        this.seatId = seatId;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.seatType = seatType;
        this.status = ShowSeatStatus.AVAILABLE;
    }
}
