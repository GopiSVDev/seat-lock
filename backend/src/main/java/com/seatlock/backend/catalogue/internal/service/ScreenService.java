package com.seatlock.backend.catalogue.internal.service;

import com.seatlock.backend.catalogue.internal.domain.Screen;
import com.seatlock.backend.catalogue.internal.domain.Seat;
import com.seatlock.backend.catalogue.internal.domain.Theatre;
import com.seatlock.backend.catalogue.internal.repository.ScreenRepository;
import com.seatlock.backend.catalogue.internal.repository.SeatRepository;
import com.seatlock.backend.catalogue.internal.repository.TheatreRepository;
import com.seatlock.backend.catalogue.internal.web.dto.CreateScreenRequest;
import com.seatlock.backend.catalogue.internal.web.dto.ScreenResponse;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScreenService {

    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public ScreenResponse create(Long theatreId, CreateScreenRequest request) {
        Theatre theatre = theatreRepository.findById(theatreId)
                .orElseThrow(() -> new NotFoundException("Theatre", theatreId));

        Screen screen = screenRepository.save(new Screen(theatre, request.name()));

        List<Seat> seats = new ArrayList<>();
        for (CreateScreenRequest.Row row : request.rows()) {
            for (int number = 1; number <= row.seatCount(); number++) {
                seats.add(new Seat(screen, row.label(), number, row.seatType()));
            }
        }

        return ScreenResponse.from(screen, seatRepository.saveAll(seats));
    }

    @Transactional(readOnly = true)
    public ScreenResponse getById(Long id) {
        Screen screen = screenRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Screen", id));
        return ScreenResponse.from(screen, seatRepository.findByScreenIdOrderByIdAsc(id));
    }
}
