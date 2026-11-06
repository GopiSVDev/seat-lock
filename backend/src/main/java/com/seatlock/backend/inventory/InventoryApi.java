package com.seatlock.backend.inventory;

import java.util.List;

public interface InventoryApi {

    // Books a hold's seats in the caller's transaction; the hold is deleted after commit.
    // Unknown, expired or someone else's hold: NotFoundException
    ConfirmedHold confirmHold(String holdId, String customerId);

    // Puts booked seats back on sale; the caller must make sure it releases a booking's seats only once
    void releaseSeats(Long showId, List<Long> showSeatIds);
}
