package com.seatlock.backend.catalogue.internal.service;

import com.seatlock.backend.catalogue.ShowScheduled;
import com.seatlock.backend.catalogue.internal.domain.Movie;
import com.seatlock.backend.catalogue.internal.domain.Screen;
import com.seatlock.backend.catalogue.internal.domain.Show;
import com.seatlock.backend.catalogue.internal.repository.MovieRepository;
import com.seatlock.backend.catalogue.internal.repository.ScreenRepository;
import com.seatlock.backend.catalogue.internal.repository.SeatRepository;
import com.seatlock.backend.catalogue.internal.repository.ShowRepository;
import com.seatlock.backend.catalogue.internal.web.dto.CreateShowRequest;
import com.seatlock.backend.catalogue.internal.web.dto.ShowResponse;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowService {

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final ApplicationEventPublisher eventPublisher;

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
                        seat.getId(), seat.getRowLabel(), seat.getSeatNumber(), seat.getSeatType().name()))
                .toList();
        
        eventPublisher.publishEvent(new ShowScheduled(show.getId(), seats));

        return ShowResponse.from(show);
    }

    @Transactional(readOnly = true)
    public ShowResponse getById(Long id) {
        return showRepository.findById(id)
                .map(ShowResponse::from)
                .orElseThrow(() -> new NotFoundException("Show", id));
    }
}
