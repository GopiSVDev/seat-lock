package com.seatlock.backend.catalogue.internal.service;

import com.seatlock.backend.catalogue.internal.domain.Movie;
import com.seatlock.backend.catalogue.internal.repository.MovieRepository;
import com.seatlock.backend.catalogue.internal.web.dto.CreateMovieRequest;
import com.seatlock.backend.catalogue.internal.web.dto.MovieResponse;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;

    @Transactional
    public MovieResponse create(CreateMovieRequest request) {
        Movie movie = new Movie(
                request.title(),
                request.description(),
                request.durationMinutes(),
                request.certification(),
                request.releaseDate()
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
