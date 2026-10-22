package com.seatlock.backend.catalogue.internal.service;

import com.seatlock.backend.catalogue.internal.domain.Theatre;
import com.seatlock.backend.catalogue.internal.repository.TheatreRepository;
import com.seatlock.backend.catalogue.internal.web.dto.CreateTheatreRequest;
import com.seatlock.backend.catalogue.internal.web.dto.TheatreResponse;
import com.seatlock.backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TheatreService {

    private final TheatreRepository theatreRepository;

    @Transactional
    public TheatreResponse create(CreateTheatreRequest request) {
        Theatre theatre = new Theatre(request.name(), request.city(), request.address());
        return TheatreResponse.from(theatreRepository.save(theatre));
    }

    @Transactional(readOnly = true)
    public TheatreResponse getById(Long id) {
        return theatreRepository.findById(id)
                .map(TheatreResponse::from)
                .orElseThrow(() -> new NotFoundException("Theatre", id));
    }
}
