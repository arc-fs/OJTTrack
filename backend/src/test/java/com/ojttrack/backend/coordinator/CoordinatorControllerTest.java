package com.ojttrack.backend.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ojttrack.backend.TestUsers;
import com.ojttrack.backend.config.PasswordConfig;
import com.ojttrack.backend.config.SecurityConfig;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import com.ojttrack.backend.user.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CoordinatorController.class)
@Import({SecurityConfig.class, PasswordConfig.class})
class CoordinatorControllerTest {

    // Spaces around the email and name on purpose: the request must normalize them.
    private static final String NEW_STUDENT = """
            {"email":"  New.Student@Test.local ","fullName":" New Student ","password":"password123"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserRepository users;

    // ---- GET /api/coordinator/students ----

    @Test
    void listRequiresLogin() throws Exception {
        mvc.perform(get("/api/coordinator/students"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotListStudents() throws Exception {
        mvc.perform(get("/api/coordinator/students").with(user(TestUsers.student(5))))
                .andExpect(status().isForbidden());
        verify(users, never()).findByCoordinatorIdOrderByFullNameAsc(any());
    }

    @Test
    void listReturnsOnlyTheLoggedInCoordinatorsStudents() throws Exception {
        when(users.findByCoordinatorIdOrderByFullNameAsc(7))
                .thenReturn(List.of(TestUsers.studentEntity(20, 7), TestUsers.studentEntity(21, 7)));

        mvc.perform(get("/api/coordinator/students").with(user(TestUsers.coordinator(7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].role").value("STUDENT"))
                // Sensitive or unneeded fields must never be sent.
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].coordinatorId").doesNotExist());

        // The coordinator id comes from the session (7), never from the request.
        verify(users).findByCoordinatorIdOrderByFullNameAsc(7);
    }

    // ---- POST /api/coordinator/students ----

    @Test
    void createdAccountIsAStudentOfTheLoggedInCoordinator() throws Exception {
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post("/api/coordinator/students")
                        .with(user(TestUsers.coordinator(7)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_STUDENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.email").value("new.student@test.local"))
                .andExpect(jsonPath("$.fullName").value("New Student"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.STUDENT);
        assertThat(saved.getValue().getCoordinatorId()).isEqualTo(7);
        // The password must be stored hashed, never as plain text.
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    void createWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/coordinator/students")
                        .with(user(TestUsers.coordinator(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_STUDENT))
                .andExpect(status().isForbidden());
        verify(users, never()).save(any());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            invalid email     | not-an-email         | Valid Name  | password123 | email
            blank email       | '   '                | Valid Name  | password123 | email
            blank full name   | ok@test.local        | '   '       | password123 | fullName
            password too short| ok@test.local        | Valid Name  | short7!     | password
            """)
    void invalidInputIsRejected(String caseName, String email, String fullName, String password,
                                String badField) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"fullName\":\"" + fullName
                + "\",\"password\":\"" + password + "\"}";

        mvc.perform(post("/api/coordinator/students")
                        .with(user(TestUsers.coordinator(1)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString(badField)));
        verify(users, never()).save(any());
    }

    @Test
    void fullNameLongerThanColumnIsRejected() throws Exception {
        String body = "{\"email\":\"ok@test.local\",\"fullName\":\"" + "A".repeat(151)
                + "\",\"password\":\"password123\"}";

        mvc.perform(post("/api/coordinator/students")
                        .with(user(TestUsers.coordinator(1)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("fullName")));
        verify(users, never()).save(any());
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        when(users.existsByEmail("new.student@test.local")).thenReturn(true);

        mvc.perform(post("/api/coordinator/students")
                        .with(user(TestUsers.coordinator(1)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_STUDENT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with that email already exists"));
        verify(users, never()).save(any());
    }

    @Test
    void studentCannotCreateAccounts() throws Exception {
        mvc.perform(post("/api/coordinator/students")
                        .with(user(TestUsers.student(5)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_STUDENT))
                .andExpect(status().isForbidden());
        verify(users, never()).save(any());
    }
}
