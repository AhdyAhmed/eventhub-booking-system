package com.ahdyahmed.eventhub.booking;

import com.ahdyahmed.eventhub.booking.dto.BookingRequest;
import com.ahdyahmed.eventhub.booking.dto.BookingResponse;

public interface BookingService {

    BookingResponse create(BookingRequest request);

    BookingResponse getById(Long id);

}
