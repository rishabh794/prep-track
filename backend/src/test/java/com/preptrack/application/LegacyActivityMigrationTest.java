package com.preptrack.application;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Upgrades a populated pre-fix schema without starting Spring or using the configured application database. */
class LegacyActivityMigrationTest {
    @Test
    void populatedLegacyDatabasePreservesHistoryAndInvalidatesAmbiguousRecallChecks() throws Exception {
        String url = "jdbc:h2:mem:legacy_activity_upgrade_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        // Holding this connection keeps only this test's database alive between Flyway's connection pools.
        try (Connection database = DriverManager.getConnection(url, "sa", "")) {
            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("14").load().migrate();
            execute(database, "INSERT INTO users (id, email, password_hash, display_name) VALUES (41, 'legacy@migration.test', 'unused-test-hash', 'Legacy learner')");
            execute(database, "INSERT INTO patterns (id, name, slug) VALUES (61, 'Legacy pattern', 'legacy-pattern')");
            execute(database, "INSERT INTO problems (id, canonical_title, difficulty) VALUES (51, 'Legacy problem', 'EASY')");
            execute(database, "INSERT INTO revision_cards (id, user_id, problem_id, next_review_date) VALUES (71, 41, 51, DATE '2026-10-04')");
            // Both have final_grade in the old schema; an override is not reliable confirmation evidence either.
            execute(database, "INSERT INTO recall_checks (id, revision_card_id, user_approach, ai_assessment, final_grade) VALUES (101, 71, 'An explanation awaiting confirmation', 'PASS', 'PASS')");
            execute(database, "INSERT INTO recall_checks (id, revision_card_id, user_approach, ai_assessment, user_override, final_grade) VALUES (102, 71, 'A previously overridden explanation', 'FAIL', 'PARTIAL', 'PARTIAL')");
            execute(database, "INSERT INTO user_problem_attempts (id, user_id, problem_id, outcome, is_revision, notes) VALUES (301, 41, 51, 'SOLVED_INDEPENDENTLY', FALSE, 'Original coding attempt')");
            execute(database, "INSERT INTO user_problem_attempts (id, user_id, problem_id, outcome, is_revision, notes) VALUES (302, 41, 51, 'SOLVED_WITH_HELP', TRUE, 'Original coding revision')");
            execute(database, "INSERT INTO applications (id, user_id, company_name) VALUES (81, 41, 'Legacy company')");
            // A later outcome edit must not erase an earlier topic confirmation.
            execute(database, "INSERT INTO interviews (id, application_id, interview_date, outcome, created_at) VALUES (201, 81, DATE '2026-10-01', 'UPCOMING', TIMESTAMP '2026-10-01 12:00:00')");
            execute(database, "INSERT INTO interview_topics (interview_id, pattern_id, was_weak, confirmed) VALUES (201, 61, TRUE, TRUE)");
            // A terminal outcome alone is not proof that topics were confirmed.
            execute(database, "INSERT INTO interviews (id, application_id, interview_date, outcome) VALUES (202, 81, DATE '2026-10-02', 'PARTIAL')");
            execute(database, "INSERT INTO interview_topics (interview_id, pattern_id, was_weak, confirmed) VALUES (202, 61, TRUE, FALSE)");

            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

            try (var query = database.createStatement(); var rows = query.executeQuery("SELECT id, legacy_invalidated, confirmed_at, user_override FROM recall_checks ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("id")).isEqualTo(101);
                assertThat(rows.getBoolean("legacy_invalidated")).isTrue();
                assertThat(rows.getTimestamp("confirmed_at")).isNull();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("id")).isEqualTo(102);
                assertThat(rows.getBoolean("legacy_invalidated")).isTrue();
                assertThat(rows.getTimestamp("confirmed_at")).isNull();
                assertThat(rows.getString("user_override")).isEqualTo("PARTIAL");
                assertThat(rows.next()).isFalse();
            }
            assertThat(text(database, "SELECT activity_type FROM user_problem_attempts WHERE id = 301")).isEqualTo("CODING_ATTEMPT");
            assertThat(text(database, "SELECT activity_type FROM user_problem_attempts WHERE id = 302")).isEqualTo("CODING_REVIEW");
            assertThat(text(database, "SELECT notes FROM user_problem_attempts WHERE id = 302")).isEqualTo("Original coding revision");
            try (var query = database.createStatement(); var rows = query.executeQuery("SELECT topics_confirmed_at FROM interviews WHERE id = 201")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getTimestamp(1).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 12, 0));
            }
            assertNullColumn(database, "SELECT topics_confirmed_at FROM interviews WHERE id = 202");

            execute(database, "INSERT INTO recall_checks (id, revision_card_id, user_approach, ai_assessment, final_grade) VALUES (103, 71, 'A fresh explanation', 'PASS', 'PASS')");
            try (var query = database.createStatement(); var rows = query.executeQuery("SELECT legacy_invalidated, confirmed_at FROM recall_checks WHERE id = 103")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean("legacy_invalidated")).isFalse();
                assertThat(rows.getTimestamp("confirmed_at")).isNull();
            }
            execute(database, "INSERT INTO interviews (id, application_id, interview_date) VALUES (203, 81, DATE '2026-11-01')");
            assertNullColumn(database, "SELECT topics_confirmed_at FROM interviews WHERE id = 203");
            execute(database, "INSERT INTO user_problem_attempts (id, user_id, problem_id, outcome, activity_type, recall_check_id) VALUES (303, 41, 51, 'SOLVED_INDEPENDENTLY', 'RECALL_REVIEW', 103)");
            assertThatThrownBy(() -> execute(database, "INSERT INTO user_problem_attempts (id, user_id, problem_id, outcome, activity_type, recall_check_id) VALUES (304, 41, 51, 'SOLVED_INDEPENDENTLY', 'RECALL_REVIEW', 103)"))
                    .isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
        }
    }

    private void execute(Connection database, String sql) throws SQLException {
        try (var query = database.createStatement()) { query.executeUpdate(sql); }
    }

    private String text(Connection database, String sql) throws SQLException {
        try (var query = database.createStatement(); var rows = query.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private void assertNullColumn(Connection database, String sql) throws SQLException {
        try (var query = database.createStatement(); var rows = query.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject(1)).isNull();
        }
    }
}
