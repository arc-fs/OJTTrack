package com.ojttrack.backend.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ojttrack.backend.TestUsers;
import com.ojttrack.backend.config.PasswordConfig;
import com.ojttrack.backend.config.SecurityConfig;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import com.ojttrack.backend.user.Role;
import org.junit.jupiter.api.Test;
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

    private static final String NEW_STUDENT = """
            {"email":"New.Student@Test.local","fullName":" New Student ","password":"password123"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserRepository users;

    @Test
    void createdAccountIsAStudent() throws Exception {
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post("/api/coordinator/students")
                        .with(user(TestUsers.coordinator(1)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_STUDENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.email").value("new.student@test.local"))
                .andExpect(jsonPath("$.fullName").value("New Student"));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.STUDENT);
        // The password must be stored hashed, never as plain text.
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123");
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
