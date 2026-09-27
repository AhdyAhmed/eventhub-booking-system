package com.ahdyahmed.eventhub.payment;

/**
 * The result of one {@link PaymentService#charge} call. {@code reason} is
 * only ever populated for a {@link PaymentStatus#FAILED} result — carried
 * through to {@link com.ahdyahmed.eventhub.payment.event.PaymentProcessedEvent}
 * so a consumer (or a recruiter reading the console log) can see *why* a
 * mock charge was declined, not just that it was.
 */
public record PaymentResult(PaymentStatus status, String reason) {

    public static PaymentResult success() {
        return new PaymentResult(PaymentStatus.SUCCEEDED, null);
    }

    public static PaymentResult failure(String reason) {
        return new PaymentResult(PaymentStatus.FAILED, reason);
    }
}
