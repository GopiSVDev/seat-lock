package com.seatlock.backend.catalogue.internal.repository;

import com.seatlock.backend.catalogue.internal.domain.CityAlias;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityAliasRepository extends JpaRepository<CityAlias, Long> {
}
