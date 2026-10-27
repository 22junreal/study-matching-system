package com.studymatching.common.persistence;
import com.studymatching.support.PostgresTestContainerConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test") @SpringBootTest @Import(PostgresTestContainerConfig.class)
class InputNormalizationMigrationTest {
    @Autowired DataSource dataSource;
    private Flyway migration(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema).target(target).load();
    }
    private void seed(String schema) throws Exception {
        migration(schema, "7").migrate();
        try (var c = dataSource.getConnection(); var s = c.createStatement()) {
            s.execute("INSERT INTO " + schema + ".members(id,username,password,email) VALUES(1,'owner','encoded','owner@test.com')");
            s.execute("INSERT INTO " + schema + ".studies(id,owner_id,title,category,level,days,start_time,end_time,max_members) VALUES(1,1,'Old',' ai ',' beginner ','wed, MONDAY,MON','19:00','21:00',3)");
            s.execute("INSERT INTO " + schema + ".profiles(id,member_id,preferred_category,level,available_days,available_time) VALUES(1,1,' ai ',' beginner ',' MON,wed,MONDAY ','19:00-21:00')");
        }
    }
    private void drop(String schema) throws Exception {
        try (var c = dataSource.getConnection(); var s = c.createStatement()) { s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }
    @Test void legacyDaysEnumsAndTimesBecomeCanonical() throws Exception {
        String schema = "normalization_valid";
        try {
            seed(schema);
            assertThat(migration(schema,"8").migrate().migrationsExecuted).isEqualTo(1);
            try (var c = dataSource.getConnection(); var s = c.createStatement()) {
                for (String table : new String[]{"study_days", "profile_days"}) {
                    try (var rows = s.executeQuery("SELECT day FROM " + schema + "." + table + " ORDER BY day")) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("MONDAY");
                        assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("WEDNESDAY");
                        assertThat(rows.next()).isFalse();
                    }
                }
                try (var rows = s.executeQuery("SELECT category,level,start_time,end_time FROM " + schema + ".profiles")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("AI");
                    assertThat(rows.getString(2)).isEqualTo("BEGINNER");
                    assertThat(rows.getTime(3).toLocalTime()).isEqualTo(java.time.LocalTime.of(19,0));
                    assertThat(rows.getTime(4).toLocalTime()).isEqualTo(java.time.LocalTime.of(21,0));
                }
            }
        } finally { drop(schema); }
    }
    @ParameterizedTest @ValueSource(strings={"day", "time", "category", "partial", "seconds", "24hour"})
    void invalidLegacyValuesAbortMigrationWithoutDataLoss(String invalid) throws Exception {
        String schema = "normalization_invalid";
        try {
            seed(schema);
            String update = switch(invalid) {
                case "day" -> ".studies SET days='SOMEDAY'";
                case "time" -> ".profiles SET available_time='24:00-25:00'";
                case "category" -> ".studies SET category='UNKNOWN'";
                case "partial" -> ".studies SET end_time=NULL";
                case "seconds" -> ".studies SET start_time='19:00:30'";
                case "24hour" -> ".studies SET end_time='24:00'";
                default -> throw new AssertionError(invalid);
            };
            try (var c = dataSource.getConnection(); var s = c.createStatement()) { s.execute("UPDATE " + schema + update); }
            String expected = switch (invalid) {
                case "day" -> "Unknown legacy day";
                case "time" -> "Invalid profiles.available_time";
                case "category" -> "chk_study_category";
                default -> "chk_study_times";
            };
            assertThatThrownBy(() -> migration(schema,"8").migrate())
                    .isInstanceOf(org.flywaydb.core.api.FlywayException.class).hasStackTraceContaining(expected);
            try (var c = dataSource.getConnection(); var s = c.createStatement();
                 var rows = s.executeQuery("SELECT days FROM " + schema + ".studies WHERE id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(invalid.equals("day") ? "SOMEDAY" : "wed, MONDAY,MON");
            }
        } finally { drop(schema); }
    }
}
