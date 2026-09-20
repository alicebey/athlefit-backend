INSERT INTO sports (id, slug, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'badminton', 'Badminton'),
    ('00000000-0000-0000-0000-000000000002', 'futsal', 'Futsal'),
    ('00000000-0000-0000-0000-000000000003', 'basketball', 'Basketball'),
    ('00000000-0000-0000-0000-000000000004', 'soccer', 'Soccer'),
    ('00000000-0000-0000-0000-000000000005', 'tennis', 'Tennis'),
    ('00000000-0000-0000-0000-000000000006', 'golf', 'Golf'),
    ('00000000-0000-0000-0000-000000000007', 'billiard', 'Billiard');

INSERT INTO venues (
    id, sport_id, name, address, latitude, longitude, rating,
    image_url, phone, open_time, close_time, hourly_rate
) VALUES
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
     'Smash Arena Kemang', 'Kemang, Jakarta Selatan', -6.260719, 106.816422, 4.7,
     NULL, NULL, '08:00', '22:00', 60000),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
     'Futsal Center Senayan', 'Senayan, Jakarta Pusat', -6.218573, 106.802567, 4.6,
     NULL, NULL, '08:00', '23:00', 120000),
    ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003',
     'Hoop Space Kuningan', 'Kuningan, Jakarta Selatan', -6.229728, 106.829518, 4.5,
     NULL, NULL, '09:00', '22:00', 90000),
    ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000004',
     'Soccer Hub Cilandak', 'Cilandak, Jakarta Selatan', -6.289239, 106.799490, 4.8,
     NULL, NULL, '07:00', '22:00', 150000),
    ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000005',
     'Menteng Tennis Court', 'Menteng, Jakarta Pusat', -6.193125, 106.834681, 4.6,
     NULL, NULL, '06:00', '21:00', 100000),
    ('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000006',
     'Jakarta Green Range', 'Pondok Indah, Jakarta Selatan', -6.265430, 106.784352, 4.9,
     NULL, NULL, '06:00', '20:00', 250000),
    ('10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000007',
     'Break Point Blok M', 'Blok M, Jakarta Selatan', -6.244564, 106.799148, 4.4,
     NULL, NULL, '10:00', '23:00', 70000);

INSERT INTO venue_open_days (venue_id, day_name)
SELECT venue.id, day_name
FROM venues venue
CROSS JOIN unnest(ARRAY[
    'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'
]) AS day_name;
