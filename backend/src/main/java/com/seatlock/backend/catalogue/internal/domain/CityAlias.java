package com.seatlock.backend.catalogue.internal.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "city_alias", schema = "catalogue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CityAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(nullable = false)
    private String alias;

    public CityAlias(City city, String alias) {
        this.city = city;
        this.alias = alias;
    }
}
