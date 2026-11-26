package com.ojttrack.backend.requirement;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A student's requirement status in numbers plus the overdue items. Used by the completion
 * review (Progress tab) and the coordinator dashboard. Callers pass ACTIVE assignments only
 * (RequirementAssignmentRepository "active" queries); archived ones are ignored here too.
 */
public record RequirementSummary(int notStarted, int submitted, int returned, int confirmed,
                                 List<OverdueItem> overdue) {

    /** Identifies what is overdue: requirement, due date and current status. */
    public record OverdueItem(Integer requirementId, String title, LocalDate dueDate, AssignmentStatus status) {}

    public static RequirementSummary of(List<RequirementAssignment> assignments,
                                        Map<Integer, Requirement> requirementsById, LocalDate today) {
        int notStarted = 0, submitted = 0, returned = 0, confirmed = 0;
        List<OverdueItem> overdue = new java.util.ArrayList<>();
        for (RequirementAssignment assignment : assignments) {
            Requirement requirement = requirementsById.get(assignment.getRequirementId());
            if (requirement == null || requirement.isArchived()) {
                continue;
            }
            switch (assignment.getStatus()) {
                case NOT_STARTED -> notStarted++;
                case SUBMITTED -> submitted++;
                case RETURNED -> returned++;
                case CONFIRMED -> confirmed++;
            }
            if (RequirementRules.isOverdue(assignment.getStatus(), requirement.getDueDate(), false, today)) {
                overdue.add(new OverdueItem(requirement.getId(), requirement.getTitle(),
                        requirement.getDueDate(), assignment.getStatus()));
            }
        }
        overdue.sort(Comparator.comparing(OverdueItem::dueDate));
        return new RequirementSummary(notStarted, submitted, returned, confirmed, List.copyOf(overdue));
    }
}
