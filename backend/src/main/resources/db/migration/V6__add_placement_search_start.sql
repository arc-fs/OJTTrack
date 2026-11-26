-- When a student is expected to be looking for an OJT placement. Set (and changed) only
-- by the student's own coordinator. Used by the coordinator dashboard's "No placement yet"
-- flag: NULL means "not set", and such a student is never flagged.
ALTER TABLE users
    ADD COLUMN placement_search_starts_on DATE;

-- Only students have this date; coordinators never do.
ALTER TABLE users
    ADD CONSTRAINT chk_users_search_start_students_only
        CHECK (placement_search_starts_on IS NULL OR role = 'STUDENT');
