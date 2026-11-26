package com.ojttrack.backend.requirement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ojttrack.backend.TestUsers;
import com.ojttrack.backend.config.PasswordConfig;
import com.ojttrack.backend.config.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** All three requirement controllers: who may call them, request validation and JSON shape. */
@WebMvcTest({CoordinatorRequirementController.class, CoordinatorRequirementReviewController.class,
        StudentRequirementController.class})
@Import({SecurityConfig.class, PasswordConfig.class})
class RequirementControllersTest {

    @Autowired MockMvc mvc;

    @MockitoBean RequirementService management;
    @MockitoBean RequirementReviewService review;
    @MockitoBean StudentRequirementService students;

    // ---- security ----

    @Test
    void everythingRequiresLogin() throws Exception {
        mvc.perform(get("/api/requirements")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/coordinator/requirements")).andExpect(status().isUnauthorized());
    }

    @Test
    void rolesOnlyReachTheirOwnEndpoints() throws Exception {
        mvc.perform(get("/api/requirements").with(user(TestUsers.coordinator(1)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/coordinator/requirements").with(user(TestUsers.student(10)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/coordinator/requirement-assignments/70/confirm").with(user(TestUsers.student(10))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        verify(review, never()).confirm(any(), any(), any());
    }

    @Test
    void writesNeedCsrf() throws Exception {
        mvc.perform(post("/api/requirements/70/submit").with(user(TestUsers.student(10)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/coordinator/requirements").with(user(TestUsers.coordinator(1)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"MOA\"}"))
                .andExpect(status().isForbidden());
        verify(students, never()).submit(any(), any(), any(), any());
        verify(management, never()).create(any(), any());
    }

    @Test
    void showArchivedIsPassedThrough() throws Exception {
        mvc.perform(get("/api/requirements?includeArchived=true").with(user(TestUsers.student(10))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/requirements").with(user(TestUsers.student(10)))).andExpect(status().isOk());
        mvc.perform(get("/api/coordinator/requirements?includeArchived=true").with(user(TestUsers.coordinator(1))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/coordinator/requirements").with(user(TestUsers.coordinator(1)))).andExpect(status().isOk());

        verify(students).list(10, true);
        verify(students).list(10, false);
        verify(management).list(1, true);
        verify(management).list(1, false);
    }

    // ---- validation ----

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\"   \"}", "{\"title\":\"x\",\"description\":\"%s\"}"})
    void createValidatesTitleAndDescription(String template) throws Exception {
        String body = template.contains("%s") ? String.format(template, "d".repeat(2001)) : template;
        mvc.perform(post("/api/coordinator/requirements").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(management, never()).create(any(), any());
    }

    @Test
    void titleIsLimitedTo200Characters() throws Exception {
        mvc.perform(post("/api/coordinator/requirements").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + "t".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editArchiveAndDeleteRequireTheVersion() throws Exception {
        mvc.perform(put("/api/coordinator/requirements/50").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"MOA\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/coordinator/requirements/50/archive").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/coordinator/requirements/50").with(user(TestUsers.coordinator(1))).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"version\":0}", "{\"version\":0,\"feedback\":\"  \"}", "{\"feedback\":\"fix it\"}"})
    void returnNeedsVersionAndRealFeedback(String body) throws Exception {
        mvc.perform(post("/api/coordinator/requirement-assignments/70/return").with(user(TestUsers.coordinator(1)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(review, never()).returnWithFeedback(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"version\":0}", "{\"version\":0,\"reason\":\"\\t \"}"})
    void unconfirmNeedsARealReason(String body) throws Exception {
        mvc.perform(post("/api/coordinator/requirement-assignments/70/unconfirm").with(user(TestUsers.coordinator(1)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitNoteIsOptionalButLimited() throws Exception {
        mvc.perform(post("/api/requirements/70/submit").with(user(TestUsers.student(10))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"note\":\"" + "n".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/requirements/70/submit").with(user(TestUsers.student(10))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isBadRequest());   // version missing
    }

    // ---- session identity and JSON ----

    @Test
    void studentSubmitUsesTheSessionStudent() throws Exception {
        when(students.submit(10, 70, 2, "done")).thenReturn(studentView(AssignmentStatus.SUBMITTED));

        mvc.perform(post("/api/requirements/70/submit").with(user(TestUsers.student(10))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2,\"note\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.history[0].action").value("SUBMITTED"));
    }

    @Test
    void coordinatorCreateUsesTheSessionCoordinator() throws Exception {
        when(management.create(eq(1), any())).thenReturn(new RequirementResponse(50, "MOA", null, null, false, null, 0,
                Instant.now(), Instant.now(), new RequirementResponse.ProgressCounts(2, 0, 0, 0)));

        mvc.perform(post("/api/coordinator/requirements").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"MOA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.progressCounts.notStarted").value(2))
                .andExpect(jsonPath("$.coordinatorId").doesNotExist());
    }

    @Test
    void deletePassesTheVersionFromTheQuery() throws Exception {
        mvc.perform(delete("/api/coordinator/requirements/50?version=4").with(user(TestUsers.coordinator(1))).with(csrf()))
                .andExpect(status().isNoContent());
        verify(management).delete(1, 50, 4);
    }

    private static StudentRequirementResponse studentView(AssignmentStatus status) {
        return new StudentRequirementResponse(70, 50, "MOA", null, LocalDate.of(2027, 1, 31), false, status, "done",
                null, Instant.now(), null, 3, false, false,
                List.of(new EventResponse(EventAction.SUBMITTED, "Student 10", "done", Instant.now())));
    }
}
