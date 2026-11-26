package com.ojttrack.backend.timelog;

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
import com.ojttrack.backend.placement.PlacementStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

/** Coordinator time log endpoints: security, request validation and JSON. Rules are in TimeLogReviewServiceTest. */
@WebMvcTest(CoordinatorTimeLogController.class)
@Import({SecurityConfig.class, PasswordConfig.class})
class CoordinatorTimeLogControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TimeLogReviewService service;

    @Test
    void queueRequiresLogin() throws Exception {
        mvc.perform(get("/api/coordinator/time-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentsCannotReviewOrComplete() throws Exception {
        mvc.perform(get("/api/coordinator/time-logs").with(user(TestUsers.student(5))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/coordinator/time-logs/7/verify").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/coordinator/placements/500/complete").with(user(TestUsers.student(5))).with(csrf()))
                .andExpect(status().isForbidden());
        verify(service, never()).verify(any(), any(), any());
        verify(service, never()).complete(any(), any());
    }

    @Test
    void verifyWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/coordinator/time-logs/7/verify").with(user(TestUsers.coordinator(1)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        verify(service, never()).verify(any(), any(), any());
    }

    @Test
    void verifyRequiresVersion() throws Exception {
        mvc.perform(post("/api/coordinator/time-logs/7/verify").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).verify(any(), any(), any());
    }

    @Test
    void verifyPassesCoordinatorLogAndVersion() throws Exception {
        when(service.verify(1, 7, 3)).thenReturn(response(TimeLogStatus.VERIFIED, null, "Coach Cruz"));

        mvc.perform(post("/api/coordinator/time-logs/7/verify").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedByName").value("Coach Cruz"))
                .andExpect(jsonPath("$.verifiedBy").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"version\":1}", "{\"version\":1,\"feedback\":\"\"}",
            "{\"version\":1,\"feedback\":\"   \"}", "{\"version\":1,\"feedback\":\"\\t\\n \"}"})
    void returnNeedsRealFeedback(String body) throws Exception {
        mvc.perform(post("/api/coordinator/time-logs/7/return").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(service, never()).returnLog(any(), any(), any(), any());
    }

    @Test
    void feedbackIsLimitedTo500Characters() throws Exception {
        mvc.perform(post("/api/coordinator/time-logs/7/return").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"feedback\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"version\":1}", "{\"version\":1,\"reason\":\"   \"}", "{\"reason\":\"wrong hours\"}"})
    void unverifyNeedsVersionAndRealReason(String body) throws Exception {
        mvc.perform(post("/api/coordinator/time-logs/7/unverify").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(service, never()).unverify(any(), any(), any(), any());
    }

    @Test
    void unverifyReturnsTheLogWithTheReasonVisible() throws Exception {
        when(service.unverify(1, 7, 4, "Wrong time out"))
                .thenReturn(response(TimeLogStatus.RETURNED, "Wrong time out", null));

        mvc.perform(post("/api/coordinator/time-logs/7/unverify").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":4,\"reason\":\"Wrong time out\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.feedback").value("Wrong time out"))
                .andExpect(jsonPath("$.verifiedAt").doesNotExist());
    }

    @Test
    void completePassesCoordinatorAndPlacement() throws Exception {
        when(service.complete(1, 500)).thenReturn(new TimeLogSummaryResponse(500, PlacementStatus.COMPLETED,
                480, 480, 0, 0, 0, 0, true, CompletionEstimate.Status.REQUIREMENT_REACHED, null, null));

        mvc.perform(post("/api/coordinator/placements/500/complete").with(user(TestUsers.coordinator(1))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placementStatus").value("COMPLETED"));
    }

    @Test
    void activePlacementsIncludeTheStudentId() throws Exception {
        when(service.activePlacements(3)).thenReturn(List.of(new CoordinatorPlacementResponse(500, 5, "Student 5", "Acme",
                java.time.LocalDate.of(2027, 1, 4), java.time.LocalDate.of(2027, 4, 30), 486,
                new TimeLogSummaryResponse(500, PlacementStatus.ACTIVE, 480, 29160, 0, 0, 0, 0, false,
                        CompletionEstimate.Status.REQUIREMENT_REACHED, null, null))));

        mvc.perform(get("/api/coordinator/placements").with(user(TestUsers.coordinator(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(5))
                .andExpect(jsonPath("$[0].studentName").value("Student 5"));
    }

    @Test
    void queueUsesTheLoggedInCoordinator() throws Exception {
        when(service.pendingQueue(3)).thenReturn(List.of(
                new ReviewQueueItem(response(TimeLogStatus.PENDING, null, null), "Student 5", "Acme")));

        mvc.perform(get("/api/coordinator/time-logs").with(user(TestUsers.coordinator(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("Student 5"))
                .andExpect(jsonPath("$[0].log.workedMinutes").value(480));
        verify(service).pendingQueue(3);
    }

    private static TimeLogResponse response(TimeLogStatus status, String feedback, String verifiedByName) {
        return new TimeLogResponse(7, 500, LocalDate.of(2027, 1, 6), LocalTime.of(8, 0), LocalTime.of(17, 0),
                false, 60, 480, "work", status, feedback, verifiedByName,
                status == TimeLogStatus.VERIFIED ? Instant.now() : null, 1, Instant.now(), Instant.now());
    }
}
