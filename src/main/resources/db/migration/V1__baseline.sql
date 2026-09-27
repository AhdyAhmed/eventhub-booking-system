-- V1__baseline.sql
--
-- Day 1: establishes the Flyway migration history for the EventHub schema.
-- No domain tables yet — Venue, Event, Seat, User, Booking, and BookingItem
-- land in V2 on Day 2 (see README.md Roadmap).
--
-- This migration exists so the pipeline (app -> Flyway -> Postgres) is proven
-- end to end before any domain modeling begins.

SELECT 1;
