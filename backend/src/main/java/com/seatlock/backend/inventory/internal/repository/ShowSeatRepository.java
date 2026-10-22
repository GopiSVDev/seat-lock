package com.seatlock.backend.inventory.internal.repository;

import com.seatlock.backend.inventory.internal.domain.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowIdOrderByIdAsc(Long showId);
}
