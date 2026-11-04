package com.seatlock.backend.catalogue.internal.service;

import com.seatlock.backend.catalogue.internal.domain.Movie;
import com.seatlock.backend.catalogue.internal.repository.MovieBrowseRepository;
import com.seatlock.backend.catalogue.internal.repository.MovieCard;
import com.seatlock.backend.catalogue.internal.repository.MovieFilter;
import com.seatlock.backend.catalogue.internal.repository.MovieRepository;
import com.seatlock.backend.catalogue.internal.web.dto.CreateMovieRequest;
import com.seatlock.backend.catalogue.internal.web.dto.MovieCardResponse;
import com.seatlock.backend.catalogue.internal.web.dto.MovieResponse;
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

    // With a city: now showing there, most upcoming shows first. Without: the whole catalogue, newest first.
    // Count and page are two statements without a shared transaction: a show starting in between can make
    // totalItems off by one for that request, harmless for a list a user is scrolling.
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
            cityService.findCity(filter.cityId());   // unknown city → 404, not an empty list
            Instant now = Instant.now();
            cards = movieBrowseRepository.findNowShowing(filter, now, size, offset);
            total = movieBrowseRepository.countNowShowing(filter, now);
        }
        return PageResponse.of(cards.stream().map(MovieCardResponse::from).toList(), page, size, total);
    }

    @Transactional
    public MovieResponse create(CreateMovieRequest request) {
        Movie movie = new Movie(
                request.title(),
                request.description(),
                request.durationMinutes(),
                request.certification(),
                request.releaseDate(),
                request.posterUrl(),
                request.genres().stream().distinct().toList()
        );
        return MovieResponse.from(movieRepository.save(movie));
    }

    @Transactional(readOnly = true)
    public MovieResponse getById(Long id) {
        return movieRepository.findById(id)
                .map(MovieResponse::from)
                .orElseThrow(() -> new NotFoundException("Movie", id));
    }
}
