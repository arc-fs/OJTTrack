package com.ojttrack.backend;

import com.ojttrack.backend.security.AppUserDetails;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.Role;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds logged-in users for controller tests without touching the database. */
public final class TestUsers {

    private TestUsers() {}

    public static AppUserDetails student(int id) {
        return user(id, "student" + id + "@test.local", Role.STUDENT);
    }

    public static AppUserDetails coordinator(int id) {
        return user(id, "coordinator" + id + "@test.local", Role.COORDINATOR);
    }

    private static AppUserDetails user(int id, String email, Role role) {
        AppUser user = new AppUser(email, "unused-hash", "Test User", role);
        // The id is normally assigned by the database; set it directly for tests.
        ReflectionTestUtils.setField(user, "id", id);
        return new AppUserDetails(user);
    }
}
