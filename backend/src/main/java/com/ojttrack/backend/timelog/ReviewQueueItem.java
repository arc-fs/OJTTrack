package com.ojttrack.backend.timelog;

/** One log in the coordinator's review queue, with who submitted it and where. */
public record ReviewQueueItem(TimeLogResponse log, String studentName, String companyName) {
}
