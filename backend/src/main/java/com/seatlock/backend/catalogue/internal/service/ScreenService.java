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
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScreenService {

    private static final double SWEET_ROW = 0.6;

    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public ScreenResponse create(Long theatreId, CreateScreenRequest request) {
        Theatre theatre = theatreRepository.findById(theatreId)
                .orElseThrow(() -> new NotFoundException("Theatre", theatreId));

        Screen screen = screenRepository.save(new Screen(theatre, request.name()));

        // Saved in row order: the seat map relies on ids following it
        int[] ranks = seatRanks(request.rows());
        List<Seat> seats = new ArrayList<>();
        for (CreateScreenRequest.Row row : request.rows()) {
            for (int number = 1; number <= row.seatCount(); number++) {
                seats.add(new Seat(screen, row.label(), number, row.seatType(), ranks[seats.size()]));
            }
        }

        return ScreenResponse.from(screen, seatRepository.saveAll(seats));
    }

    // Rank 1 = closest to the sweet spot (row 60% back, centre); a row away counts as 2 seats.
    // Ties: front row, then left seat.
    private static int[] seatRanks(List<CreateScreenRequest.Row> rows) {
        record Place(int index, int row, int number, double distance) {
        }

        double sweetRow = SWEET_ROW * (rows.size() - 1);
        List<Place> places = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            double centre = (rows.get(r).seatCount() + 1) / 2.0;
            for (int number = 1; number <= rows.get(r).seatCount(); number++) {
                places.add(new Place(places.size(), r, number, 2 * Math.abs(r - sweetRow) + Math.abs(number - centre)));
            }
        }

        places.sort(Comparator.comparingDouble(Place::distance)
                .thenComparingInt(Place::row)
                .thenComparingInt(Place::number));
        int[] ranks = new int[places.size()];
        for (int i = 0; i < places.size(); i++) {
            ranks[places.get(i).index()] = i + 1;
        }
        return ranks;
    }

    @Transactional(readOnly = true)
    public ScreenResponse getById(Long id) {
        Screen screen = screenRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Screen", id));
        return ScreenResponse.from(screen, seatRepository.findByScreenIdOrderByIdAsc(id));
    }
}
