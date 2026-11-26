package com.ojttrack.backend.placement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves V8 is safe for data that already exists: a placement created under V7 keeps
 * every value, and its new role column is NULL (shown as "Role not recorded").
 *
 * Runs the project's real migrations in a throwaway schema, so the normal test schema
 * is never touched. Not transactional: Flyway uses its own connections.
 * Needs ojttrack_test (see application-dbtest.properties). From backend/:
 *   .\mvnw.cmd test "-Dtest=*DbTest"   (with DB_PASSWORD set)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dbtest")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlacementRoleMigrationDbTest {

    static final String SCHEMA = "v8_migration_check";

    @Autowired DataSource dataSource;
    JdbcTemplate jdbc;

    @BeforeEach
    void freshSchema() {
        jdbc = new JdbcTemplate(dataSource);
        dropSchema();   // in case an earlier run stopped halfway
    }

    @AfterEach
    void dropSchema() {
        new JdbcTemplate(dataSource).execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void existingPlacementsSurviveV8WithNoRole() {
        migrateTo("7");
        Integer coordinator = insertUser("mig-coord@test.local", "COORDINATOR", null);
        Integer student = insertUser("mig-student@test.local", "STUDENT", coordinator);
        Integer application = jdbc.queryForObject("INSERT INTO " + SCHEMA + ".applications "
                + "(student_id, company_name, status) VALUES (?, 'Acme', 'ACCEPTED') RETURNING id", Integer.class, student);
        Integer placement = jdbc.queryForObject("INSERT INTO " + SCHEMA + ".placements "
                + "(application_id, student_id, status, start_date, end_date, required_hours) "
                + "VALUES (?, ?, 'ACTIVE', DATE '2027-01-04', DATE '2027-04-30', 486) RETURNING id",
                Integer.class, application, student);

        MigrateResult result = migrateTo("8");

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("8");
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM " + SCHEMA + ".placements WHERE id = ?", placement);
        assertThat(row.get("application_id")).isEqualTo(application);
        assertThat(row.get("student_id")).isEqualTo(student);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(((java.sql.Date) row.get("start_date")).toLocalDate()).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(((java.sql.Date) row.get("end_date")).toLocalDate()).isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(row.get("required_hours")).isEqualTo(486);
        assertThat(row).containsKey("role");
        assertThat(row.get("role")).isNull();
    }

    private MigrateResult migrateTo(String version) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private Integer insertUser(String email, String role, Integer coordinatorId) {
        return jdbc.queryForObject("INSERT INTO " + SCHEMA + ".users "
                + "(email, password_hash, full_name, role, coordinator_id) VALUES (?, 'hash', ?, ?, ?) RETURNING id",
                Integer.class, email, email, role, coordinatorId);
    }
}
