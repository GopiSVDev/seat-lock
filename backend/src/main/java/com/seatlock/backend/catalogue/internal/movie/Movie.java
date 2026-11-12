package com.seatlock.backend.catalogue.internal.movie;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    private String posterUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<Genre> genres;

    public Movie(String title, String description, int durationMinutes, Certification certification,
            LocalDate releaseDate, String posterUrl, List<Genre> genres) {
        this.title = title;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.certification = certification;
        this.releaseDate = releaseDate;
        this.posterUrl = posterUrl;
        this.genres = new ArrayList<>(genres);
    }
}
