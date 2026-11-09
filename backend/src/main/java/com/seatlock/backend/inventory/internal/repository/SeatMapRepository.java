package com.seatlock.backend.inventory.internal.repository;

import com.seatlock.backend.inventory.internal.domain.SeatType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;

// Plain JDBC: hot read path.
@Repository
@RequiredArgsConstructor
public class SeatMapRepository {

    private final JdbcClient jdbcClient;

    public List<LayoutSeat> findLayout(Long showId) {
        return jdbcClient.sql("""
                        select id, row_label, seat_number, seat_type, price
                        from inventory.show_seat
                        where show_id = :showId
                        order by seat_id
                        """)
                .param("showId", showId)
                .query((rs, rowNum) -> new LayoutSeat(
                        rs.getLong("id"),
                        rs.getString("row_label"),
                        rs.getInt("seat_number"),
                        SeatType.valueOf(rs.getString("seat_type")),
                        rs.getLong("price")))
                .list();
    }

    // total_seats = 0: unknown show.
    public SeatAvailability findAvailability(Long showId) {
        return jdbcClient.sql("""
                        select count(*) as total_seats,
                               array_agg(id order by seat_id) filter (where status <> 'AVAILABLE') as unavailable
                        from inventory.show_seat
                        where show_id = :showId
                        """)
                .param("showId", showId)
                .query((rs, rowNum) -> new SeatAvailability(
                        rs.getInt("total_seats"),
                        toIds(rs.getArray("unavailable"))))
                .single();
    }

    private static List<Long> toIds(Array array) throws SQLException {
        return array == null ? List.of() : List.of((Long[]) array.getArray());
    }
}
