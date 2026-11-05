package com.seatlock.backend.catalogue.internal.screen;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreenIdOrderByIdAsc(Long screenId);
}
