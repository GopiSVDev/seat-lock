package com.seatlock.backend.booking.internal.domain;

import com.seatlock.backend.catalogue.ShowSummary;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// The show as it was when booked, like an order line keeping the product name
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowSnapshot {

    @Column(name = "show_start_time", nullable = false)
    private Instant startTime;

    @Column(name = "show_language", nullable = false)
    private String language;

    @Column(name = "show_format", nullable = false)
    private String format;

    @Column(nullable = false)
    private Long movieId;

    @Column(nullable = false)
    private String movieTitle;

    @Column(length = 500)
    private String moviePosterUrl;

    @Column(nullable = false)
    private String movieCertification;

    @Column(nullable = false)
    private Long theatreId;

    @Column(nullable = false)
    private String theatreName;

    @Column(nullable = false)
    private String screenName;

    public ShowSnapshot(ShowSummary show) {
        this.startTime = show.startTime();
        this.language = show.language();
        this.format = show.format();
        this.movieId = show.movieId();
        this.movieTitle = show.movieTitle();
        this.moviePosterUrl = show.moviePosterUrl();
        this.movieCertification = show.movieCertification();
        this.theatreId = show.theatreId();
        this.theatreName = show.theatreName();
        this.screenName = show.screenName();
    }
}
