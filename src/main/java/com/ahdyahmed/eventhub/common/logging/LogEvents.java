package com.ahdyahmed.eventhub.common.logging;

/**
 * Machine-readable names for the lifecycle events this project logs, emitted
 * as an {@code "event"} field on the JSON log line.
 *
 * <p>The human-readable log message is free to be reworded at any time; the
 * {@code event} value is the stable handle a dashboard or an alert rule
 * queries on ({@code event:"payment.processed" AND paymentStatus:"FAILED"}),
 * so it changes only deliberately. Naming convention:
 * {@code <domain>.<what happened>}.</p>
 */
public final class LogEvents {

    /** One line per finished HTTP request — see {@code RequestLoggingFilter}. */
    public static final String HTTP_REQUEST_COMPLETED = "http.request.completed";

    /** A booking was created and its transaction committed. */
    public static final String BOOKING_CREATED = "booking.created";

    /** A booking moved between {@code BookingStatus} values (payment result, cancellation). */
    public static final String BOOKING_STATUS_CHANGED = "booking.status_changed";

    /** The mock payment step produced a result (succeeded or declined) for a booking. */
    public static final String PAYMENT_PROCESSED = "payment.processed";

    /** The notification consumer "sent" its (mock) confirmation email. */
    public static final String NOTIFICATION_SENT = "notification.sent";

    private LogEvents() {
    }
}
