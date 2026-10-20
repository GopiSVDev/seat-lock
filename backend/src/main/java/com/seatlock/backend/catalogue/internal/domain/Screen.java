package com.seatlock.backend.catalogue.internal.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "screen", schema = "catalogue",
        uniqueConstraints = @UniqueConstraint(columnNames = {"theatre_id", "name"}))
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theatre_id")
    private Theatre theatre;

    @Column(nullable = false)
    private String name;

    public Long getId() {
        return id;
    }

    public Theatre getTheatre() {
        return theatre;
    }

    public String getName() {
        return name;
    }
}
