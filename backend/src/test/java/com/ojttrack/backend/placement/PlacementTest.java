package com.ojttrack.backend.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The Placement factory enforces the role itself, whatever the caller validated (MVP 4.3). */
class PlacementTest {

    static final LocalDate START = LocalDate.of(2027, 1, 4);
    static final LocalDate END = LocalDate.of(2027, 4, 30);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void aNewPlacementNeedsARole(String role) {
        assertThatThrownBy(() -> Placement.activate(100, 10, START, END, 486, role))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a role");
    }

    @Test
    void theRoleIsStoredTrimmed() {
        Placement placement = Placement.activate(100, 10, START, END, 486, "  Web Developer Intern  ");

        assertThat(placement.getRole()).isEqualTo("Web Developer Intern");
        assertThat(placement.getStatus()).isEqualTo(PlacementStatus.ACTIVE);
    }
}
