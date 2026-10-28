package com.seatlock.backend.inventory.internal.repository;

import com.seatlock.backend.inventory.internal.domain.SeatType;
import com.seatlock.backend.inventory.internal.domain.ShowSeatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

// Plain JDBC for the hot read path; writes go through ShowSeatRepository (JPA).
@Repository
@RequiredArgsConstructor
public class SeatMapRepository {

    private final JdbcClient jdbcClient;

    public List<SeatMapSeat> findSeatMap(Long showId) {
        return jdbcClient.sql("""
                        select id, row_label, seat_number, seat_type, status
                        from inventory.show_seat
                        where show_id = :showId
                        order by seat_id
                        """)
                .param("showId", showId)
                .query((rs, rowNum) -> new SeatMapSeat(
                        rs.getLong("id"),
                        rs.getString("row_label"),
                        rs.getInt("seat_number"),
                        SeatType.valueOf(rs.getString("seat_type")),
                        ShowSeatStatus.valueOf(rs.getString("status"))))
                .list();
    }
}
