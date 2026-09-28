-- Day 16: users need a password to authenticate against. This column was
-- deliberately left out of V2's users table - User.java's own class doc
-- said auth was "out of scope here" until the auth module landed - so this
-- is a follow-up ALTER (like V3's seats.version before it), not part of
-- V2 itself.
--
-- NOT NULL with an empty-string default, not a nullable column: a user
-- without a password isn't a valid row in a system where every user
-- authenticates by password, so the column shouldn't allow it going
-- forward. The empty-string default exists only to satisfy that constraint
-- for whatever rows already exist in a local dev database from earlier
-- days' manual testing - those users simply can't log in with an empty
-- hash and would need to re-register. This is a demo project's local
-- Postgres container, not a production migration with real user data to
-- preserve; a real system would backfill or force a password reset instead
-- of a silent empty default.
ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE users
    ALTER COLUMN password_hash DROP DEFAULT;
