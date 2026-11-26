package com.ojttrack.backend.requirement;

import java.time.Instant;

/** One history step, with who did it (name, not id) and its own note or reason. */
public record EventResponse(EventAction action, String actorName, String text, Instant createdAt) {
}
