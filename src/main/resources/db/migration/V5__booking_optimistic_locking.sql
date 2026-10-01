-- Prevent payment settlement and user cancellation from silently
-- overwriting one another when they race on the same booking.
ALTER TABLE bookings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
