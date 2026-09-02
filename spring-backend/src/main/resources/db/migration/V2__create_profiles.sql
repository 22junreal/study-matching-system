CREATE TABLE profiles (
                          id BIGSERIAL PRIMARY KEY,
                          member_id BIGINT NOT NULL UNIQUE,
                          department VARCHAR(100),
                          preferred_category VARCHAR(50),
                          level VARCHAR(50),
                          available_days VARCHAR(100),
                          available_time VARCHAR(100),

                          CONSTRAINT fk_profiles_member
                              FOREIGN KEY (member_id)
                                  REFERENCES members(id)
                                  ON DELETE CASCADE
);