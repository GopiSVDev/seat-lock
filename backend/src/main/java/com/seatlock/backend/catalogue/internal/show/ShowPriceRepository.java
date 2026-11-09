package com.seatlock.backend.catalogue.internal.show;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowPriceRepository extends JpaRepository<ShowPrice, Long> {

    List<ShowPrice> findByShowId(Long showId);
}
