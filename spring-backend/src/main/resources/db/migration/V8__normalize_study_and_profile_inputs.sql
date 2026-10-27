-- Abort on unrecognized legacy values. Never silently discard a day or truncate time.
CREATE OR REPLACE FUNCTION pg_temp.canonical_day(value TEXT) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE normalized TEXT := upper(trim(value));
BEGIN
    RETURN CASE normalized
        WHEN 'MON' THEN 'MONDAY' WHEN 'MONDAY' THEN 'MONDAY'
        WHEN 'TUE' THEN 'TUESDAY' WHEN 'TUESDAY' THEN 'TUESDAY'
        WHEN 'WED' THEN 'WEDNESDAY' WHEN 'WEDNESDAY' THEN 'WEDNESDAY'
        WHEN 'THU' THEN 'THURSDAY' WHEN 'THURSDAY' THEN 'THURSDAY'
        WHEN 'FRI' THEN 'FRIDAY' WHEN 'FRIDAY' THEN 'FRIDAY'
        WHEN 'SAT' THEN 'SATURDAY' WHEN 'SATURDAY' THEN 'SATURDAY'
        WHEN 'SUN' THEN 'SUNDAY' WHEN 'SUNDAY' THEN 'SUNDAY'
        ELSE NULL END;
END $$;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM studies, unnest(string_to_array(days, ',')) token
               WHERE nullif(trim(days), '') IS NOT NULL AND pg_temp.canonical_day(token) IS NULL)
       OR EXISTS (SELECT 1 FROM profiles, unnest(string_to_array(available_days, ',')) token
               WHERE nullif(trim(available_days), '') IS NOT NULL AND pg_temp.canonical_day(token) IS NULL)
    THEN RAISE EXCEPTION 'Unknown legacy day: inspect studies.days and profiles.available_days before V8'; END IF;
    IF EXISTS (SELECT 1 FROM profiles WHERE available_time IS NOT NULL AND
        trim(available_time) !~ '^([01][0-9]|2[0-3]):[0-5][0-9]-([01][0-9]|2[0-3]):[0-5][0-9]$')
    THEN RAISE EXCEPTION 'Invalid profiles.available_time: expected HH:mm-HH:mm before V8'; END IF;
END $$;
CREATE TABLE study_days (
    study_id BIGINT NOT NULL REFERENCES studies(id) ON DELETE CASCADE,
    day VARCHAR(9) NOT NULL CHECK (day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    PRIMARY KEY (study_id, day)
);
CREATE TABLE profile_days (
    profile_id BIGINT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    day VARCHAR(9) NOT NULL CHECK (day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    PRIMARY KEY (profile_id, day)
);
INSERT INTO study_days SELECT DISTINCT id, pg_temp.canonical_day(token)
FROM studies, unnest(string_to_array(days, ',')) token WHERE nullif(trim(days), '') IS NOT NULL;
INSERT INTO profile_days SELECT DISTINCT id, pg_temp.canonical_day(token)
FROM profiles, unnest(string_to_array(available_days, ',')) token WHERE nullif(trim(available_days), '') IS NOT NULL;
ALTER TABLE studies DROP COLUMN days;
ALTER TABLE profiles DROP COLUMN available_days;
ALTER TABLE profiles RENAME COLUMN preferred_category TO category;
ALTER TABLE profiles ADD COLUMN start_time TIME, ADD COLUMN end_time TIME;
UPDATE profiles SET start_time = split_part(trim(available_time), '-', 1)::TIME,
                    end_time = split_part(trim(available_time), '-', 2)::TIME
WHERE available_time IS NOT NULL;
ALTER TABLE profiles DROP COLUMN available_time;
UPDATE studies SET category = upper(trim(category)), level = upper(trim(level));
UPDATE profiles SET category = upper(trim(category)), level = upper(trim(level));
ALTER TABLE studies ADD CONSTRAINT chk_study_category CHECK (category IN ('PROGRAMMING','AI','DATA','LANGUAGE','CERTIFICATE','ETC')),
    ADD CONSTRAINT chk_study_level CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    ADD CONSTRAINT chk_study_times CHECK ((start_time IS NULL AND end_time IS NULL) OR
        (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time AND end_time < TIME '24:00'
         AND extract(second FROM start_time) = 0 AND extract(second FROM end_time) = 0));
ALTER TABLE profiles ADD CONSTRAINT chk_profile_category CHECK (category IN ('PROGRAMMING','AI','DATA','LANGUAGE','CERTIFICATE','ETC')),
    ADD CONSTRAINT chk_profile_level CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    ADD CONSTRAINT chk_profile_times CHECK ((start_time IS NULL AND end_time IS NULL) OR
        (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time AND end_time < TIME '24:00'
         AND extract(second FROM start_time) = 0 AND extract(second FROM end_time) = 0));

-- Connections may be pooled; do not leave migration helpers on a reused session.
DROP FUNCTION pg_temp.canonical_day(TEXT);
