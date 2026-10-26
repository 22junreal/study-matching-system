package com.studymatching.common.persistence;

import com.studymatching.support.PostgresTestContainerConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class StudyApplicationVersionMigrationTest {
    @Autowired DataSource dataSource;

    @Test
    void versionMigrationBackfillsExistingApplicationsWithoutChangingTheirState() throws Exception {
        // A separate schema models a populated V5 database without touching JPA's schema.
        String schema = "application_version_migration_test";
        try {
            Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                    .target("5").load().migrate();
            try (Connection connection = dataSource.getConnection();
                 Statement sql = connection.createStatement()) {
                sql.execute("""
                        INSERT INTO application_version_migration_test.members (id, username, password, email) VALUES
                        (1, 'owner', 'encoded', 'owner@test.com'),
                        (2, 'applicant', 'encoded', 'applicant@test.com')
                        """);
                sql.execute("""
                        INSERT INTO application_version_migration_test.studies (id, owner_id, title, category, level, max_members)
                        VALUES (1, 1, 'Existing study', 'PROGRAMMING', 'BEGINNER', 3)
                        """);
                sql.execute("""
                        INSERT INTO application_version_migration_test.study_applications (id, study_id, applicant_id, status)
                        VALUES (1, 1, 2, 'PENDING')
                        """);
            }
            Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                    .target("6").load().migrate();
            try (Connection connection = dataSource.getConnection();
                 Statement sql = connection.createStatement();
                 ResultSet row = sql.executeQuery(
                         "SELECT status, version FROM " + schema + ".study_applications WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("status")).isEqualTo("PENDING");
                assertThat(row.getLong("version")).isZero();
                assertThat(row.wasNull()).isFalse();
            }
        } finally {
            try (Connection connection = dataSource.getConnection();
                 Statement sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
