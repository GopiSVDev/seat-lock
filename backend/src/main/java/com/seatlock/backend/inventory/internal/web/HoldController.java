package com.seatlock.backend.inventory.internal.web;

import com.seatlock.backend.inventory.internal.service.SeatHoldService;
import com.seatlock.backend.inventory.internal.web.dto.CreateBestAvailableHoldRequest;
import com.seatlock.backend.inventory.internal.web.dto.CreateHoldRequest;
import com.seatlock.backend.inventory.internal.web.dto.HoldResponse;
import com.seatlock.backend.shared.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/holds")
@RequiredArgsConstructor
public class HoldController {

    private final SeatHoldService seatHoldService;

    @PostMapping
    public ResponseEntity<HoldResponse> hold(@Valid @RequestBody CreateHoldRequest request, @AuthenticationPrincipal Jwt jwt) {
        return created(seatHoldService.hold(request.showId(), request.showSeatIds(), CurrentUser.from(jwt).id()));
    }

    @PostMapping("/best-available")
    public ResponseEntity<HoldResponse> holdBestAvailable(@Valid @RequestBody CreateBestAvailableHoldRequest request, @AuthenticationPrincipal Jwt jwt) {
        return created(seatHoldService.holdBestAvailable(request.showId(), request.quantity(), request.seatType(),
                CurrentUser.from(jwt).id()));
    }

    @GetMapping("/{id}")
    public HoldResponse get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return seatHoldService.get(id, CurrentUser.from(jwt).id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        seatHoldService.release(id, CurrentUser.from(jwt).id());
    }

    private static ResponseEntity<HoldResponse> created(HoldResponse hold) {
        return ResponseEntity.created(URI.create("/holds/" + hold.id())).body(hold);
    }
}
