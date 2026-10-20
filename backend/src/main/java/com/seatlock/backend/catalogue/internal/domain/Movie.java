package com.seatlock.backend.catalogue.internal.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "movie", schema = "catalogue")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Certification certification;

    @Column(nullable = false)
    private LocalDate releaseDate;

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Certification getCertification() {
        return certification;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }
}
