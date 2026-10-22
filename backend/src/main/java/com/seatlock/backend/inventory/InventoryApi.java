package com.seatlock.backend.inventory;

import java.util.List;

public interface InventoryApi {
    List<BookedSeat> bookSeats(Long showId, List<Long> showSeatIds);
}
