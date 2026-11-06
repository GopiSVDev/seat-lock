package com.seatlock.backend.inventory.internal.repository;

import com.seatlock.backend.inventory.BookedSeat;
import com.seatlock.backend.inventory.internal.domain.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    // Native for RETURNING: books and reads labels in one round trip.
    @Query(value = """
            update inventory.show_seat
            set status = 'BOOKED'
            where id in (:ids)
              and show_id = :showId
              and status = 'AVAILABLE'
            returning id as showSeatId, row_label || seat_number as label
            """, nativeQuery = true)
    List<BookedSeat> bookIfAvailable(Long showId, Collection<Long> ids);

    // No row locks: holds in Redis decide who gets a seat; held seats are filtered out by the caller.
    @Query(value = """
            select id, row_label as rowLabel, seat_number as seatNumber, seat_rank as seatRank
            from inventory.show_seat
            where show_id = :showId
              and seat_type = :seatType
              and status = 'AVAILABLE'
            order by seat_rank
            """, nativeQuery = true)
    List<CandidateSeat> findAvailable(Long showId, String seatType);

    @Query(value = """
            select id as showSeatId, show_id as showId, row_label || seat_number as label, status
            from inventory.show_seat
            where id in (:ids)
            order by seat_id
            """, nativeQuery = true)
    List<SeatState> findStates(Collection<Long> ids);

    @Modifying
    @Query(value = """
            update inventory.show_seat
            set status = 'AVAILABLE'
            where id in (:ids)
              and show_id = :showId
              and status = 'BOOKED'
            """, nativeQuery = true)
    int release(Long showId, Collection<Long> ids);

    boolean existsByShowId(Long showId);
}
