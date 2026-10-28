package com.seatlock.backend.inventory.internal.repository;

import com.seatlock.backend.inventory.internal.domain.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    @Query("""
            select new com.seatlock.backend.inventory.internal.repository.SeatMapSeat(
                s.id, s.rowLabel, s.seatNumber, s.seatType, s.status)
            from ShowSeat s
            where s.showId = :showId
            order by s.seatId
            """)
    List<SeatMapSeat> findSeatMap(Long showId);

    @Modifying
    @Query("""
            update ShowSeat s
            set s.status = com.seatlock.backend.inventory.internal.domain.ShowSeatStatus.BOOKED
            where s.id in :ids
              and s.showId = :showId
              and s.status = com.seatlock.backend.inventory.internal.domain.ShowSeatStatus.AVAILABLE
            """)
    int bookIfAvailable(Long showId, Collection<Long> ids);
}
