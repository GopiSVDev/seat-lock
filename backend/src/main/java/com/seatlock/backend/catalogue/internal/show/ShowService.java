package com.seatlock.backend.catalogue.internal.show;

import com.seatlock.backend.catalogue.CatalogueApi;
import com.seatlock.backend.catalogue.ShowScheduled;
import com.seatlock.backend.catalogue.ShowSummary;
import com.seatlock.backend.catalogue.internal.city.CityService;
import com.seatlock.backend.catalogue.internal.movie.Movie;
import com.seatlock.backend.catalogue.internal.movie.MovieRepository;
import com.seatlock.backend.catalogue.internal.screen.Screen;
import com.seatlock.backend.catalogue.internal.screen.ScreenRepository;
import com.seatlock.backend.catalogue.internal.screen.Seat;
import com.seatlock.backend.catalogue.internal.screen.SeatRepository;
import com.seatlock.backend.catalogue.internal.screen.SeatType;
import com.seatlock.backend.catalogue.internal.show.dto.CreateShowRequest;
import com.seatlock.backend.catalogue.internal.show.dto.ShowResponse;
import com.seatlock.backend.catalogue.internal.show.dto.ShowtimesResponse.Showtime;
import com.seatlock.backend.catalogue.internal.show.dto.ShowtimesResponse.TheatreShows;
import com.seatlock.backend.catalogue.internal.show.dto.ShowtimesResponse;
import com.seatlock.backend.catalogue.internal.show.dto.TheatreShowtimesResponse.MovieShows;
import com.seatlock.backend.catalogue.internal.show.dto.TheatreShowtimesResponse;
import com.seatlock.backend.catalogue.internal.theatre.Theatre;
import com.seatlock.backend.catalogue.internal.theatre.TheatreRepository;
import com.seatlock.backend.catalogue.internal.theatre.dto.TheatreResponse;
import com.seatlock.backend.shared.exception.BadRequestException;
import com.seatlock.backend.shared.exception.NotFoundException;
import com.seatlock.backend.shared.time.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowService implements CatalogueApi {

    private static final int DATE_STRIP_DAYS = 7;

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ShowtimeRepository showtimeRepository;
    private final CityService cityService;
    private final TheatreRepository theatreRepository;
    private final ShowPriceRepository showPriceRepository;

    @Transactional
    public ShowResponse create(CreateShowRequest request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new NotFoundException("Movie", request.movieId()));
        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() -> new NotFoundException("Screen", request.screenId()));

        List<Seat> screenSeats = seatRepository.findByScreenIdOrderByIdAsc(screen.getId());
        Map<SeatType, Long> prices = request.prices();
        Set<SeatType> seatTypes = screenSeats.stream().map(Seat::getSeatType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SeatType.class)));
        if (!seatTypes.equals(prices.keySet())) {
            throw new BadRequestException("prices must cover exactly the screen's seat types " + seatTypes);
        }

        Show show = showRepository
                .save(new Show(movie, screen, request.startTime(), request.language(), request.format()));
        List<ShowPrice> showPrices = showPriceRepository
                .saveAll(seatTypes.stream().map(type -> new ShowPrice(show, type, prices.get(type))).toList());

        List<ShowScheduled.ScheduledSeat> seats = screenSeats.stream()
                .map(seat -> new ShowScheduled.ScheduledSeat(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(),
                        seat.getSeatType().name(), seat.getSeatRank(), prices.get(seat.getSeatType())))
                .toList();

        eventPublisher.publishEvent(new ShowScheduled(show.getId(), seats));
        log.info("Show {} scheduled: movie {}, screen {}, starts {}, {} seats", show.getId(), movie.getId(),
                screen.getId(), show.getStartTime(), seats.size());

        return ShowResponse.from(show, showPrices);
    }

    // Not paged: one movie, city and day is small.
    public ShowtimesResponse findShowtimes(Long movieId, Long cityId, LocalDate date, List<Language> languages,
            List<ShowFormat> formats) {
        if (!movieRepository.existsById(movieId)) {
            throw new NotFoundException("Movie", movieId);
        }
        cityService.findCity(cityId);

        LocalDate day = dayOrToday(date);
        ShowScope scope = ShowScope.movieInCity(movieId, cityId);
        Instant now = Instant.now();
        List<ShowtimeRow> rows = showtimeRepository.findShowtimes(scope, languages, formats, now,
                IndiaTime.startOf(day), IndiaTime.startOf(day.plusDays(1)));
        List<LocalDate> dates = showtimeRepository.findDates(scope, languages, formats, now, DATE_STRIP_DAYS);

        return new ShowtimesResponse(movieId, cityId, day, dates, groupByTheatre(rows));
    }

    // Transactional: the response reads the theatre's lazy city.
    @Transactional(readOnly = true)
    public TheatreShowtimesResponse findTheatreShowtimes(Long theatreId, LocalDate date, List<Language> languages,
            List<ShowFormat> formats) {
        Theatre theatre = theatreRepository.findById(theatreId)
                .orElseThrow(() -> new NotFoundException("Theatre", theatreId));

        LocalDate day = dayOrToday(date);
        ShowScope scope = ShowScope.atTheatre(theatreId);
        Instant now = Instant.now();
        List<ShowtimeRow> rows = showtimeRepository.findShowtimes(scope, languages, formats, now,
                IndiaTime.startOf(day), IndiaTime.startOf(day.plusDays(1)));
        List<LocalDate> dates = showtimeRepository.findDates(scope, languages, formats, now, DATE_STRIP_DAYS);

        return new TheatreShowtimesResponse(TheatreResponse.from(theatre), day, dates, groupByMovie(rows));
    }

    private static LocalDate dayOrToday(LocalDate date) {
        LocalDate today = IndiaTime.today();
        LocalDate day = date == null ? today : date;
        if (day.isBefore(today)) {
            throw new BadRequestException("date " + day + " is in the past (today in India is " + today + ")");
        }
        return day;
    }

    private static List<MovieShows> groupByMovie(List<ShowtimeRow> rows) {
        Map<Long, MovieShows> movies = new LinkedHashMap<>();
        for (ShowtimeRow row : rows) {
            movies.computeIfAbsent(row.movieId(), id -> new MovieShows(id, row.movieTitle(), row.moviePosterUrl(),
                    row.movieCertification(), row.movieDurationMinutes(), new ArrayList<>())).shows()
                    .add(showtime(row));
        }
        return List.copyOf(movies.values());
    }

    private static Showtime showtime(ShowtimeRow row) {
        return new Showtime(row.showId(), row.startTime(), row.language(), row.format(), row.screenName());
    }

    private static List<TheatreShows> groupByTheatre(List<ShowtimeRow> rows) {
        Map<Long, TheatreShows> theatres = new LinkedHashMap<>();
        for (ShowtimeRow row : rows) {
            theatres.computeIfAbsent(row.theatreId(),
                    id -> new TheatreShows(id, row.theatreName(), row.theatreAddress(), new ArrayList<>())).shows()
                    .add(showtime(row));
        }
        return List.copyOf(theatres.values());
    }

    @Transactional(readOnly = true)
    public ShowResponse getById(Long id) {
        return showRepository.findById(id).map(show -> ShowResponse.from(show, showPriceRepository.findByShowId(id)))
                .orElseThrow(() -> new NotFoundException("Show", id));
    }

    @Override
    public ShowSummary findShowSummary(Long showId) {
        Show show = showRepository.findWithDetailsById(showId).orElseThrow(() -> new NotFoundException("Show", showId));
        Movie movie = show.getMovie();
        Theatre theatre = show.getScreen().getTheatre();
        return new ShowSummary(show.getId(), show.getStartTime(), show.getLanguage().name(), show.getFormat().name(),
                movie.getId(), movie.getTitle(), movie.getPosterUrl(), movie.getCertification().name(), theatre.getId(),
                theatre.getName(), show.getScreen().getName());
    }
}
