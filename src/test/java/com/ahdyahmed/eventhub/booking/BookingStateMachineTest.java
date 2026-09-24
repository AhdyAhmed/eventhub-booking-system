package com.ahdyahmed.eventhub.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ahdyahmed.eventhub.common.exception.InvalidBookingStateTransitionException;
import org.junit.jupiter.api.Test;

class BookingStateMachineTest {

    private final BookingStateMachine stateMachine = new BookingStateMachine();

    private Booking bookingWithStatus(BookingStatus status) {
        Booking booking = Booking.builder().status(status).build();
        booking.setId(1L);
        return booking;
    }

    @Test
    void pendingToConfirmed_isAllowed() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);

        stateMachine.transition(booking, BookingStatus.CONFIRMED);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void pendingToFailed_isAllowed() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);

        stateMachine.transition(booking, BookingStatus.FAILED);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
    }

    @Test
    void pendingToCancelled_isAllowed() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);

        stateMachine.transition(booking, BookingStatus.CANCELLED);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void confirmedToCancelled_isAllowed() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);

        stateMachine.transition(booking, BookingStatus.CANCELLED);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void sameStatus_isIdempotentNoOp() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);

        stateMachine.transition(booking, BookingStatus.CONFIRMED);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmedToFailed_isRejected() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);

        assertThatThrownBy(() -> stateMachine.transition(booking, BookingStatus.FAILED))
                .isInstanceOf(InvalidBookingStateTransitionException.class)
                .hasMessageContaining("CONFIRMED")
                .hasMessageContaining("FAILED");
    }

    @Test
    void failedIsTerminal_everyOutgoingTransitionIsRejected() {
        Booking booking = bookingWithStatus(BookingStatus.FAILED);

        assertThatThrownBy(() -> stateMachine.transition(booking, BookingStatus.CONFIRMED))
                .isInstanceOf(InvalidBookingStateTransitionException.class);
        assertThatThrownBy(() -> stateMachine.transition(booking, BookingStatus.CANCELLED))
                .isInstanceOf(InvalidBookingStateTransitionException.class);
    }

    @Test
    void cancelledIsTerminal_everyOutgoingTransitionIsRejected() {
        Booking booking = bookingWithStatus(BookingStatus.CANCELLED);

        assertThatThrownBy(() -> stateMachine.transition(booking, BookingStatus.CONFIRMED))
                .isInstanceOf(InvalidBookingStateTransitionException.class);
        assertThatThrownBy(() -> stateMachine.transition(booking, BookingStatus.PENDING))
                .isInstanceOf(InvalidBookingStateTransitionException.class);
    }
}
