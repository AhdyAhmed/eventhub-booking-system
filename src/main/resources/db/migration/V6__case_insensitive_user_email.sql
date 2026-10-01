-- Authentication treats email addresses case-insensitively. Enforce the
-- same identity rule in PostgreSQL so differently-cased registrations
-- cannot create two accounts that authenticate as the same address.
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));
