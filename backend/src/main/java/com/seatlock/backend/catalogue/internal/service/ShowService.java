package com.seatlock.backend.catalogue.internal.service;

import com.seatlock.backend.catalogue.ShowScheduled;
import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.Movie;
import com.seatlock.backend.catalogue.internal.domain.Screen;
import com.seatlock.backend.catalogue.internal.domain.Show;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;
import com.seatlock.backend.catalogue.internal.repository.MovieRepository;
import com.seatlock.backend.catalogue.internal.repository.ScreenRepository;
import com.seatlock.backend.catalogue.internal.repository.SeatRepository;
import com.seatlock.backend.catalogue.internal.repository.ShowRepository;
import com.seatlock.backend.catalogue.internal.repository.ShowtimeRepository;
import com.seatlock.backend.catalogue.internal.repository.ShowtimeRow;
import com.seatlock.backend.catalogue.internal.web.dto.CreateShowRequest;
import com.seatlock.backend.catalogue.internal.web.dto.ShowResponse;
import com.seatlock.backend.catalogue.internal.web.dto.ShowtimesResponse;
import com.seatlock.backend.catalogue.internal.web.dto.ShowtimesResponse.Showtime;
import com.seatlock.backend.catalogue.internal.web.dto.ShowtimesResponse.TheatreShows;
import com.seatlock.backend.shared.exception.BadRequestException;
import com.seatlock.backend.shared.exception.NotFoundException;
import com.seatlock.backend.shared.time.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShowService {

    // Days shown in the date strip
    private static final int DATE_STRIP_DAYS = 7;

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ShowtimeRepository showtimeRepository;
    private final CityService cityService;

    @Transactional
    public ShowResponse create(CreateShowRequest request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new NotFoundException("Movie", request.movieId()));
        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() -> new NotFoundException("Screen", request.screenId()));

        Show show = showRepository.save(
                new Show(movie, screen, request.startTime(), request.language(), request.format()));

        List<ShowScheduled.ScheduledSeat> seats = seatRepository.findByScreenIdOrderByIdAsc(screen.getId()).stream()
                .map(seat -> new ShowScheduled.ScheduledSeat(
                        seat.getId(), seat.getRowLabel(), seat.getSeatNumber(), seat.getSeatType().name(),
                        seat.getSeatRank()))
                .toList();
        
        eventPublisher.publishEvent(new ShowScheduled(show.getId(), seats));

        return ShowResponse.from(show);
    }

    // Shows of a movie in a city on one date in India (default today), not yet started, grouped by theatre.
    // Not paged: one movie, one city, one day is naturally small (tens of theatres, a few shows each).
    public ShowtimesResponse findShowtimes(Long movieId, Long cityId, LocalDate date,
                                           List<Language> languages, List<ShowFormat> formats) {
        if (!movieRepository.existsById(movieId)) {
            throw new NotFoundException("Movie", movieId);
        }
        cityService.findCity(cityId);

        LocalDate today = IndiaTime.today();
        LocalDate day = date == null ? today : date;
        if (day.isBefore(today)) {
            throw new BadRequestException("date " + day + " is in the past (today in India is " + today + ")");
        }

        Instant now = Instant.now();
        List<ShowtimeRow> rows = showtimeRepository.findShowtimes(movieId, cityId, languages, formats, now,
                IndiaTime.startOf(day), IndiaTime.startOf(day.plusDays(1)));
        List<LocalDate> dates = showtimeRepository.findDates(movieId, cityId, languages, formats, now, DATE_STRIP_DAYS);

        return new ShowtimesResponse(movieId, cityId, day, dates, groupByTheatre(rows));
    }

    // Rows arrive sorted by theatre, then time; keep that order
    private static List<TheatreShows> groupByTheatre(List<ShowtimeRow> rows) {
        Map<Long, TheatreShows> theatres = new LinkedHashMap<>();
        for (ShowtimeRow row : rows) {
            theatres.computeIfAbsent(row.theatreId(), id ->
                            new TheatreShows(id, row.theatreName(), row.theatreAddress(), new ArrayList<>()))
                    .shows()
                    .add(new Showtime(row.showId(), row.startTime(), row.language(), row.format(), row.screenName()));
        }
        return List.copyOf(theatres.values());
    }

    @Transactional(readOnly = true)
    public ShowResponse getById(Long id) {
        return showRepository.findById(id)
                .map(ShowResponse::from)
                .orElseThrow(() -> new NotFoundException("Show", id));
    }
}
