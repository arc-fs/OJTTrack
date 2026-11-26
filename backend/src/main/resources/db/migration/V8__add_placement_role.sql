-- The job title the coordinator records when approving a placement (MVP 4.3).
-- NULL only for placements created before this migration: nothing is invented for them.
-- The API (ApprovePlacementRequest) and Placement.activate require a role for every new one.
ALTER TABLE placements ADD COLUMN role VARCHAR(150);   -- same length as applications.position (V2)

ALTER TABLE placements ADD CONSTRAINT chk_placements_role_not_blank
    CHECK (role IS NULL OR role ~ '[^[:space:]]');
