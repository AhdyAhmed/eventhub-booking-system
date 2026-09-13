package com.ahdyahmed.eventhub.booking;

import com.ahdyahmed.eventhub.booking.dto.BookingItemResponse;
import com.ahdyahmed.eventhub.booking.dto.BookingResponse;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getUser().getId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getItems().stream().map(this::toItemResponse).toList(),
                booking.getCreatedAt()
        );
    }

    private BookingItemResponse toItemResponse(BookingItem item) {
        return new BookingItemResponse(
                item.getSeat().getId(),
                item.getSeat().getSeatNumber(),
                item.getPriceAtBooking()
        );
    }

}
