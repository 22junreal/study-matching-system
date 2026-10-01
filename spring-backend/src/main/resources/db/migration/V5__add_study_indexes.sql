CREATE INDEX idx_studies_category
    ON studies (category);

CREATE INDEX idx_studies_level
    ON studies (level);

CREATE INDEX idx_studies_status
    ON studies (status);

CREATE INDEX idx_studies_owner_id
    ON studies (owner_id);