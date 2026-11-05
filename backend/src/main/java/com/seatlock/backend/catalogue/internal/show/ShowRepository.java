package com.seatlock.backend.catalogue.internal.show;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ShowRepository extends JpaRepository<Show, Long> {

    // One query instead of lazy-loading movie, screen and theatre one by one
    @Query("""
            select s from Show s
            join fetch s.movie
            join fetch s.screen sc
            join fetch sc.theatre
            where s.id = :id
            """)
    Optional<Show> findWithDetailsById(Long id);
}
