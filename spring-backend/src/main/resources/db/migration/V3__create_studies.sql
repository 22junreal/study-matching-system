CREATE TABLE studies (
                         id BIGSERIAL PRIMARY KEY,

                         owner_id BIGINT NOT NULL,

                         title VARCHAR(100) NOT NULL,
                         description TEXT,

                         category VARCHAR(30) NOT NULL,
                         level VARCHAR(30) NOT NULL,

                         days VARCHAR(100),
                         start_time TIME,
                         end_time TIME,

                         max_members INTEGER NOT NULL,

                         status VARCHAR(30) NOT NULL DEFAULT 'RECRUITING',

                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT fk_studies_owner
                             FOREIGN KEY (owner_id)
                                 REFERENCES members(id),

                         CONSTRAINT chk_studies_max_members
                             CHECK (max_members >= 1)
);