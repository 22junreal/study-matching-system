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
class RefreshTokenMigrationTest {
    @Autowired DataSource dataSource;

    @Test
    void v10PreservesMembersAndEnforcesHashAndSessionConstraintsWithCascadeCleanup() throws Exception {
        String schema = "refresh_migration_test";
        try {
            Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                    .target("9").load().migrate();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("INSERT INTO refresh_migration_test.members(id, username, password, email) VALUES (1, 'existing', 'encoded', 'existing@test.com')");
            }
            var flyway = Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                    .target("10").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                try (var rows = sql.executeQuery("SELECT username, password FROM refresh_migration_test.members WHERE id = 1")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("existing");
                    assertThat(rows.getString(2)).isEqualTo("encoded");
                }
                sql.execute("""
                        INSERT INTO refresh_migration_test.refresh_sessions(id, member_id, created_at, expires_at)
                        VALUES ('00000000-0000-0000-0000-000000000001', 1, now(), now() + interval '14 days')
                        """);
                String insert = """
                        INSERT INTO refresh_migration_test.refresh_tokens(token_hash, session_id, created_at)
                        VALUES (repeat('a', 64), '00000000-0000-0000-0000-000000000001', now())
                        """;
                sql.execute(insert);
                assertThatThrownBy(() -> sql.execute(insert)).isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getSQLState()).isEqualTo("23505"));
                assertThatThrownBy(() -> sql.execute(insert.replace("repeat('a', 64)", "'raw-token'")))
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
                assertThatThrownBy(() -> sql.execute(insert.replace("repeat('a', 64)", "repeat('b', 64)")
                        .replace("00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002")))
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));
                sql.execute("DELETE FROM refresh_migration_test.members WHERE id = 1");
                for (String table : new String[]{"refresh_sessions", "refresh_tokens"}) {
                    try (var rows = sql.executeQuery("SELECT count(*) FROM refresh_migration_test." + table)) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getLong(1)).isZero();
                    }
                }
            }
        } finally {
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS refresh_migration_test CASCADE");
            }
        }
    }
}
