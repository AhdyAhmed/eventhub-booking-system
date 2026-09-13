-- V3__seat_optimistic_locking.sql
--
-- Day 6: adds the @Version column that makes optimistic locking possible on
-- seats. Every UPDATE Hibernate issues against this table now includes
-- "AND version = ?" in the WHERE clause and "version = version + 1" in the
-- SET clause; if a concurrent transaction already moved a seat out from
-- under us, the update matches zero rows and Hibernate raises an optimistic
-- lock failure instead of silently clobbering someone else's reservation.
--
-- DEFAULT 0 backfills existing rows (there aren't any yet in a fresh
-- environment, but this keeps the migration valid against any environment
-- that already has seed data).

ALTER TABLE seats
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
