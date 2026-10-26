package com.studymatching.common.persistence;

import com.studymatching.support.PostgresTestContainerConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class StudyMemberMigrationTest {
    @Autowired DataSource dataSource;

    @Test
    void v7BackfillsOwnerAndApprovedOnlyAndPreservesApplicationHistory() throws Exception {
        String schema = "study_member_migration_test";
        try {
            Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                    .target("6").load().migrate();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("""
                        INSERT INTO study_member_migration_test.members(id, username, password, email) VALUES
                        (1, 'owner', 'encoded', 'owner@test.com'),
                        (2, 'approved', 'encoded', 'approved@test.com'),
                        (3, 'pending', 'encoded', 'pending@test.com'),
                        (4, 'canceled', 'encoded', 'canceled@test.com'),
                        (5, 'rejected', 'encoded', 'rejected@test.com')
                        """);
                sql.execute("""
                        INSERT INTO study_member_migration_test.studies(id, owner_id, title, category, level, max_members, status)
                        VALUES (1, 1, 'Existing study', 'PROGRAMMING', 'BEGINNER', 2, 'CLOSED')
                        """);
                sql.execute("""
                        INSERT INTO study_member_migration_test.study_applications(study_id, applicant_id, status, version) VALUES
                        (1, 1, 'APPROVED', 3), (1, 2, 'APPROVED', 2), (1, 3, 'PENDING', 0),
                        (1, 4, 'CANCELED', 1), (1, 5, 'REJECTED', 1)
                        """);
            }
            Flyway flyway = Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                    .target("7").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                try (var rows = sql.executeQuery("SELECT member_id, status FROM study_member_migration_test.study_members ORDER BY member_id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("member_id")).isEqualTo(1);
                    assertThat(rows.getString("status")).isEqualTo("ACTIVE");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("member_id")).isEqualTo(2);
                    assertThat(rows.getString("status")).isEqualTo("ACTIVE");
                    assertThat(rows.next()).isFalse();
                }
                try (var rows = sql.executeQuery("SELECT status, version FROM study_member_migration_test.study_applications ORDER BY applicant_id")) {
                    String[] statuses = {"APPROVED", "APPROVED", "PENDING", "CANCELED", "REJECTED"};
                    long[] versions = {3, 2, 0, 1, 1};
                    for (int i = 0; i < statuses.length; i++) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString("status")).isEqualTo(statuses[i]);
                        assertThat(rows.getLong("version")).isEqualTo(versions[i]);
                    }
                }
                assertThatThrownBy(() -> sql.execute(
                        "INSERT INTO study_member_migration_test.study_members(study_id, member_id, status) VALUES (1, 2, 'ACTIVE')"))
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
                sql.execute("DELETE FROM study_member_migration_test.studies WHERE id = 1");
                try (var rows = sql.executeQuery("SELECT count(*) FROM study_member_migration_test.study_members")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isZero();
                }
            }
        } finally {
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
