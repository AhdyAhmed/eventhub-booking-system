-- Opt-in EventHub portfolio data. Run through scripts/seed-demo.ps1 after
-- Flyway has created the schema. This script is intentionally not a Flyway
-- migration: production-like environments should never receive demo rows
-- merely because the application started.

BEGIN;

INSERT INTO venues (name, city, address, capacity)
SELECT 'Cairo Arena', 'Cairo', 'Nasr City, Cairo', 5000
WHERE NOT EXISTS (
    SELECT 1 FROM venues WHERE name = 'Cairo Arena' AND city = 'Cairo'
);

INSERT INTO venues (name, city, address, capacity)
SELECT 'Nile Expo Center', 'Giza', 'Corniche El Nile, Giza', 2400
WHERE NOT EXISTS (
    SELECT 1 FROM venues WHERE name = 'Nile Expo Center' AND city = 'Giza'
);

UPDATE venues
SET address = 'Nasr City, Cairo', capacity = 5000, updated_at = now()
WHERE id = (SELECT min(id) FROM venues WHERE name = 'Cairo Arena' AND city = 'Cairo');

UPDATE venues
SET address = 'Corniche El Nile, Giza', capacity = 2400, updated_at = now()
WHERE id = (SELECT min(id) FROM venues WHERE name = 'Nile Expo Center' AND city = 'Giza');

INSERT INTO events (venue_id, name, description, category, event_date)
SELECT v.id, 'Cairo Tech Summit',
       'A full-day backend engineering conference with live architecture sessions.',
       'CONFERENCE', now() + interval '60 days'
FROM venues v
WHERE v.id = (SELECT min(id) FROM venues WHERE name = 'Cairo Arena' AND city = 'Cairo')
  AND NOT EXISTS (SELECT 1 FROM events WHERE name = 'Cairo Tech Summit');

INSERT INTO events (venue_id, name, description, category, event_date)
SELECT v.id, 'Nile Jazz Night',
       'An evening concert featuring contemporary Egyptian jazz artists.',
       'CONCERT', now() + interval '90 days'
FROM venues v
WHERE v.id = (SELECT min(id) FROM venues WHERE name = 'Nile Expo Center' AND city = 'Giza')
  AND NOT EXISTS (SELECT 1 FROM events WHERE name = 'Nile Jazz Night');

INSERT INTO events (venue_id, name, description, category, event_date)
SELECT v.id, 'Championship Final',
       'The season final with reserved seating across pitch and grandstand sections.',
       'SPORTS', now() + interval '120 days'
FROM venues v
WHERE v.id = (SELECT min(id) FROM venues WHERE name = 'Cairo Arena' AND city = 'Cairo')
  AND NOT EXISTS (SELECT 1 FROM events WHERE name = 'Championship Final');

UPDATE events
SET event_date = now() + interval '60 days', updated_at = now()
WHERE id = (SELECT min(id) FROM events WHERE name = 'Cairo Tech Summit');

UPDATE events
SET event_date = now() + interval '90 days', updated_at = now()
WHERE id = (SELECT min(id) FROM events WHERE name = 'Nile Jazz Night');

UPDATE events
SET event_date = now() + interval '120 days', updated_at = now()
WHERE id = (SELECT min(id) FROM events WHERE name = 'Championship Final');

WITH demo_seats(event_name, seat_number, section, price) AS (
    VALUES
        ('Cairo Tech Summit', 'A1', 'Main Hall', 120.00::numeric),
        ('Cairo Tech Summit', 'A2', 'Main Hall', 120.00::numeric),
        ('Cairo Tech Summit', 'A3', 'Main Hall', 120.00::numeric),
        ('Cairo Tech Summit', 'A4', 'Main Hall', 120.00::numeric),
        ('Cairo Tech Summit', 'B1', 'Workshop', 85.00::numeric),
        ('Cairo Tech Summit', 'B2', 'Workshop', 85.00::numeric),
        ('Cairo Tech Summit', 'B3', 'Workshop', 85.00::numeric),
        ('Cairo Tech Summit', 'B4', 'Workshop', 85.00::numeric),
        ('Nile Jazz Night', 'T1', 'Terrace', 180.00::numeric),
        ('Nile Jazz Night', 'T2', 'Terrace', 180.00::numeric),
        ('Nile Jazz Night', 'T3', 'Terrace', 180.00::numeric),
        ('Nile Jazz Night', 'T4', 'Terrace', 180.00::numeric),
        ('Nile Jazz Night', 'S1', 'Stage Front', 260.00::numeric),
        ('Nile Jazz Night', 'S2', 'Stage Front', 260.00::numeric),
        ('Nile Jazz Night', 'S3', 'Stage Front', 260.00::numeric),
        ('Nile Jazz Night', 'S4', 'Stage Front', 260.00::numeric),
        ('Championship Final', 'P1', 'Pitch Side', 350.00::numeric),
        ('Championship Final', 'P2', 'Pitch Side', 350.00::numeric),
        ('Championship Final', 'P3', 'Pitch Side', 350.00::numeric),
        ('Championship Final', 'P4', 'Pitch Side', 350.00::numeric),
        ('Championship Final', 'G1', 'Grandstand', 150.00::numeric),
        ('Championship Final', 'G2', 'Grandstand', 150.00::numeric),
        ('Championship Final', 'G3', 'Grandstand', 150.00::numeric),
        ('Championship Final', 'G4', 'Grandstand', 150.00::numeric)
), selected_events AS (
    SELECT e.id, e.name
    FROM events e
    WHERE e.id = (SELECT min(e2.id) FROM events e2 WHERE e2.name = e.name)
      AND e.name IN ('Cairo Tech Summit', 'Nile Jazz Night', 'Championship Final')
)
INSERT INTO seats (event_id, seat_number, section, price, status)
SELECT e.id, s.seat_number, s.section, s.price, 'AVAILABLE'
FROM demo_seats s
JOIN selected_events e ON e.name = s.event_name
ON CONFLICT (event_id, seat_number) DO UPDATE
SET section = EXCLUDED.section,
    price = EXCLUDED.price,
    updated_at = now();

COMMIT;

SELECT e.id AS event_id,
       e.name,
       v.city,
       e.category,
       e.event_date,
       count(s.id) AS seats
FROM events e
JOIN venues v ON v.id = e.venue_id
LEFT JOIN seats s ON s.event_id = e.id
WHERE e.id IN (
    SELECT min(e2.id)
    FROM events e2
    WHERE e2.name IN ('Cairo Tech Summit', 'Nile Jazz Night', 'Championship Final')
    GROUP BY e2.name
)
GROUP BY e.id, e.name, v.city, e.category, e.event_date
ORDER BY e.event_date;
