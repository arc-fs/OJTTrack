package com.ojttrack.backend;

import com.ojttrack.backend.security.AppUserDetails;
import com.ojttrack.backend.user.AppUser;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds users for controller tests without touching the database. */
public final class TestUsers {

    /** The coordinator that test students belong to unless a test says otherwise. */
    public static final int DEFAULT_COORDINATOR_ID = 1;

    private TestUsers() {}

    public static AppUserDetails student(int id) {
        return new AppUserDetails(studentEntity(id, DEFAULT_COORDINATOR_ID));
    }

    public static AppUserDetails coordinator(int id) {
        AppUser user = AppUser.coordinator("coordinator" + id + "@test.local", "unused-hash", "Coordinator " + id);
        return new AppUserDetails(withId(user, id));
    }

    public static AppUser studentEntity(int id, int coordinatorId) {
        AppUser user = AppUser.student("student" + id + "@test.local", "unused-hash", "Student " + id, coordinatorId);
        return withId(user, id);
    }

    // The id is normally assigned by the database; set it directly for tests.
    private static AppUser withId(AppUser user, int id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
