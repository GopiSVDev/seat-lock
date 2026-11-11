package com.seatlock.backend.catalogue.internal.movie;

import com.seatlock.backend.catalogue.internal.city.CityService;
import com.seatlock.backend.catalogue.internal.movie.dto.CreateMovieRequest;
import com.seatlock.backend.catalogue.internal.movie.dto.MovieCardResponse;
import com.seatlock.backend.catalogue.internal.movie.dto.MovieResponse;
import com.seatlock.backend.shared.exception.BadRequestException;
import com.seatlock.backend.shared.exception.NotFoundException;
import com.seatlock.backend.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final MovieBrowseRepository movieBrowseRepository;
    private final CityService cityService;

    // Count and page run separately: totalItems can be off by one if a show starts in between.
    public PageResponse<MovieCardResponse> find(MovieFilter filter, int page, int size) {
        long offset = (long) page * size;
        List<MovieCard> cards;
        long total;
        if (filter.cityId() == null) {
            if (!filter.languages().isEmpty() || !filter.formats().isEmpty()) {
                throw new BadRequestException("language and format filter shows in a city: add cityId");
            }
            cards = movieBrowseRepository.findAll(filter, size, offset);
            total = movieBrowseRepository.countAll(filter);
        } else {
            cityService.findCity(filter.cityId()); // unknown city → 404, not an empty list
            Instant now = Instant.now();
            cards = movieBrowseRepository.findNowShowing(filter, now, size, offset);
            total = movieBrowseRepository.countNowShowing(filter, now);
        }
        return PageResponse.of(cards.stream().map(MovieCardResponse::from).toList(), page, size, total);
    }

    @Transactional
    public MovieResponse create(CreateMovieRequest request) {
        Movie movie = new Movie(request.title(), request.description(), request.durationMinutes(),
                request.certification(), request.releaseDate(), request.posterUrl(),
                request.genres().stream().distinct().toList());
        return MovieResponse.from(movieRepository.save(movie));
    }

    @Transactional(readOnly = true)
    public MovieResponse getById(Long id) {
        return movieRepository.findById(id).map(MovieResponse::from)
                .orElseThrow(() -> new NotFoundException("Movie", id));
    }
}
