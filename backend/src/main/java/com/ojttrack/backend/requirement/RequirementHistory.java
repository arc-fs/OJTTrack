package com.ojttrack.backend.requirement;

import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Loads the history of several assignments at once (one query for events, one for names). */
final class RequirementHistory {

    private RequirementHistory() {}

    static Map<Integer, List<EventResponse>> byAssignment(Collection<Integer> assignmentIds,
                                                          RequirementEventRepository events,
                                                          AppUserRepository users) {
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        List<RequirementEvent> found = events.findByAssignmentIdInOrderByCreatedAtAscIdAsc(assignmentIds);
        Map<Integer, String> names = names(found.stream().map(RequirementEvent::getActorId).toList(), users);
        return found.stream().collect(Collectors.groupingBy(RequirementEvent::getAssignmentId,
                Collectors.mapping(e -> new EventResponse(e.getAction(), names.get(e.getActorId()), e.getText(),
                        e.getCreatedAt()), Collectors.toList())));
    }

    static Map<Integer, String> names(Collection<Integer> userIds, AppUserRepository users) {
        List<Integer> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
    }
}
