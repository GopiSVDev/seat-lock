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

    // SKIP LOCKED: concurrent buyers lock different seats instead of waiting.
    @Query(value = """
            select id, row_label as rowLabel, seat_number as seatNumber, seat_rank as seatRank
            from inventory.show_seat
            where show_id = :showId
              and seat_type = :seatType
              and status = 'AVAILABLE'
              and seat_rank > :afterRank
            order by seat_rank
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<CandidateSeat> lockAvailable(Long showId, String seatType, int afterRank, int limit);

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
