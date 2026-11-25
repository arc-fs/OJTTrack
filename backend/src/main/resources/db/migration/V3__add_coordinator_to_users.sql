-- Each student belongs to the coordinator who manages them.
-- Coordinators themselves have no coordinator.

ALTER TABLE users
    ADD COLUMN coordinator_id INTEGER REFERENCES users (id);

-- Backfill: students created before this migration are assigned to the
-- first coordinator (lowest id). Students can only be created by a
-- coordinator, so if any student exists, a coordinator exists too.
UPDATE users
SET coordinator_id = (SELECT MIN(id) FROM users WHERE role = 'COORDINATOR')
WHERE role = 'STUDENT';

-- From now on the database guarantees the rule, whatever the Java code does.
ALTER TABLE users
    ADD CONSTRAINT chk_users_coordinator CHECK (
        (role = 'STUDENT' AND coordinator_id IS NOT NULL)
        OR (role = 'COORDINATOR' AND coordinator_id IS NULL)
    );

CREATE INDEX idx_users_coordinator_id
    ON users (coordinator_id);
