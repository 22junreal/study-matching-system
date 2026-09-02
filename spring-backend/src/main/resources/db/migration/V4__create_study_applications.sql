CREATE TABLE study_applications (
                                    id BIGSERIAL PRIMARY KEY,

                                    study_id BIGINT NOT NULL,
                                    applicant_id BIGINT NOT NULL,

                                    status VARCHAR(30) NOT NULL,

                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_study_applications_study
                                        FOREIGN KEY (study_id)
                                            REFERENCES studies(id)
                                            ON DELETE CASCADE,

                                    CONSTRAINT fk_study_applications_applicant
                                        FOREIGN KEY (applicant_id)
                                            REFERENCES members(id)
                                            ON DELETE CASCADE,

                                    CONSTRAINT uk_study_applications_study_applicant
                                        UNIQUE (study_id, applicant_id)
);