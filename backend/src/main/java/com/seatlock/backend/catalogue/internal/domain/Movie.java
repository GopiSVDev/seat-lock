package com.seatlock.backend.catalogue.internal.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "movie", schema = "catalogue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    public Movie(String title, String description, int durationMinutes, Certification certification, LocalDate releaseDate) {
        this.title = title;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.certification = certification;
        this.releaseDate = releaseDate;
    }
}
