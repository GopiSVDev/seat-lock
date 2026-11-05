package com.seatlock.backend.inventory;

import java.util.List;

public interface InventoryApi {
    List<BookedSeat> bookSeats(Long showId, List<Long> showSeatIds);

    // Books the best `quantity` adjacent seats of one type (REGULAR, PREMIUM, RECLINER) in one row
    List<BookedSeat> bookBestAvailable(Long showId, int quantity, String seatType);

    // Puts booked seats back on sale; the caller must make sure it releases a booking's seats only once
    void releaseSeats(Long showId, List<Long> showSeatIds);
}
