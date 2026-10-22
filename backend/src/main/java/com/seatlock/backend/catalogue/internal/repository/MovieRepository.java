package com.seatlock.backend.catalogue.internal.repository;

import com.seatlock.backend.catalogue.internal.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
