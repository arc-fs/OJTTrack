package com.ojttrack.backend.requirement;

import static com.ojttrack.backend.requirement.AssignmentStatus.CONFIRMED;
import static com.ojttrack.backend.requirement.AssignmentStatus.NOT_STARTED;
import static com.ojttrack.backend.requirement.AssignmentStatus.RETURNED;
import static com.ojttrack.backend.requirement.AssignmentStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The developer's due-date rules, one test each. Pure: plain dates, no mocks, no clock. */
class RequirementRulesTest {

    static final LocalDate TODAY = LocalDate.of(2027, 1, 20);

    @ParameterizedTest
    @EnumSource(AssignmentStatus.class)
    void noDueDateIsNeverOverdueOrDueSoon(AssignmentStatus status) {
        assertThat(RequirementRules.isOverdue(status, null, false, TODAY)).isFalse();
        assertThat(RequirementRules.isDueSoon(status, null, false, TODAY)).isFalse();
    }

    @Test
    void dueTodayIsNotYetOverdueTheDayAfterIs() {
        assertThat(RequirementRules.isOverdue(NOT_STARTED, TODAY, false, TODAY)).isFalse();
        assertThat(RequirementRules.isOverdue(NOT_STARTED, TODAY.minusDays(1), false, TODAY)).isTrue();
    }

    @Test
    void submittedIsNeverOverdueBecauseReviewDelaysArentTheStudentsFault() {
        assertThat(RequirementRules.isOverdue(SUBMITTED, TODAY.minusDays(30), false, TODAY)).isFalse();
    }

    @Test
    void returnedBecomesOverdueAgainAfterTheDueDate() {
        assertThat(RequirementRules.isOverdue(RETURNED, TODAY.minusDays(1), false, TODAY)).isTrue();
    }

    @Test
    void confirmedIsNeverOverdue() {
        assertThat(RequirementRules.isOverdue(CONFIRMED, TODAY.minusDays(30), false, TODAY)).isFalse();
    }

    @Test
    void archivedIsNeverOverdueOrDueSoon() {
        assertThat(RequirementRules.isOverdue(NOT_STARTED, TODAY.minusDays(5), true, TODAY)).isFalse();
        assertThat(RequirementRules.isDueSoon(NOT_STARTED, TODAY.plusDays(1), true, TODAY)).isFalse();
    }

    @Test
    void dueSoonWindowIsTodayThroughSevenDays() {
        assertThat(RequirementRules.isDueSoon(NOT_STARTED, TODAY, false, TODAY)).isTrue();
        assertThat(RequirementRules.isDueSoon(NOT_STARTED, TODAY.plusDays(7), false, TODAY)).isTrue();
        assertThat(RequirementRules.isDueSoon(NOT_STARTED, TODAY.plusDays(8), false, TODAY)).isFalse();
        assertThat(RequirementRules.isDueSoon(NOT_STARTED, TODAY.minusDays(1), false, TODAY)).isFalse();  // overdue instead
        assertThat(RequirementRules.isDueSoon(SUBMITTED, TODAY.plusDays(2), false, TODAY)).isFalse();
        assertThat(RequirementRules.isDueSoon(RETURNED, TODAY.plusDays(2), false, TODAY)).isTrue();
    }

    @Test
    void aChangedDueDateTakesEffectImmediately() {
        // Nothing is stored: the same assignment is overdue with the old date and fine with the new one.
        assertThat(RequirementRules.isOverdue(NOT_STARTED, TODAY.minusDays(1), false, TODAY)).isTrue();
        assertThat(RequirementRules.isOverdue(NOT_STARTED, TODAY.plusDays(10), false, TODAY)).isFalse();
    }
}
