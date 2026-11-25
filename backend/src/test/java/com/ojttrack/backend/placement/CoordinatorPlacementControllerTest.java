package com.ojttrack.backend.placement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Coordinator placement-request endpoints: security, validation and JSON. */
@WebMvcTest(CoordinatorPlacementController.class)
@Import({SecurityConfig.class, PasswordConfig.class})
class CoordinatorPlacementControllerTest {

    private static final String APPROVE_BODY =
            "{\"startDate\":\"2027-01-04\",\"endDate\":\"2027-04-30\",\"requiredHours\":486,\"note\":\"ok\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlacementService service;

    @Test
    void pendingListRequiresLogin() throws Exception {
        mvc.perform(get("/api/coordinator/placement-requests")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotSeeOrDecideRequests() throws Exception {
        mvc.perform(get("/api/coordinator/placement-requests").with(user(TestUsers.student(5))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/coordinator/placement-requests/7/approve")
                        .with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                .andExpect(status().isForbidden());
        verify(service, never()).approve(any(), any(), any(), any(), any(), any());
    }

    @Test
    void pendingListUsesLoggedInCoordinator() throws Exception {
        when(service.pendingRequestsFor(3)).thenReturn(List.of(response(PlacementRequestStatus.PENDING)));

        mvc.perform(get("/api/coordinator/placement-requests").with(user(TestUsers.coordinator(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("Student 5"))
                .andExpect(jsonPath("$[0].type").value("CHANGE"));
        verify(service).pendingRequestsFor(3);
    }

    @Test
    void approvePassesTermsAndCoordinatorToService() throws Exception {
        when(service.approve(eq(3), eq(7), eq(LocalDate.of(2027, 1, 4)), eq(LocalDate.of(2027, 4, 30)),
                eq(486), eq("ok"))).thenReturn(response(PlacementRequestStatus.APPROVED));

        mvc.perform(post("/api/coordinator/placement-requests/7/approve")
                        .with(user(TestUsers.coordinator(3))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/coordinator/placement-requests/7/approve")
                        .with(user(TestUsers.coordinator(3)))
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                .andExpect(status().isForbidden());
        verify(service, never()).approve(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"endDate\":\"2027-04-30\",\"requiredHours\":486}",                          // no startDate
            "{\"startDate\":\"2027-01-04\",\"requiredHours\":486}",                        // no endDate
            "{\"startDate\":\"2027-01-04\",\"endDate\":\"2027-04-30\"}",                   // no hours
            "{\"startDate\":\"2027-01-04\",\"endDate\":\"2027-04-30\",\"requiredHours\":0}",
            "{\"startDate\":\"2027-01-04\",\"endDate\":\"2027-04-30\",\"requiredHours\":-5}",
            "{\"startDate\":\"2027-01-04\",\"endDate\":\"2027-04-30\",\"requiredHours\":5001}",
            "{\"startDate\":\"not-a-date\",\"endDate\":\"2027-04-30\",\"requiredHours\":486}"
    })
    void approveValidatesBody(String body) throws Exception {
        mvc.perform(post("/api/coordinator/placement-requests/7/approve")
                        .with(user(TestUsers.coordinator(3))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(service, never()).approve(any(), any(), any(), any(), any(), any());
    }

    @Test
    void approvingAnotherCoordinatorsRequestIsNotFound() throws Exception {
        when(service.approve(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Placement request not found"));

        mvc.perform(post("/api/coordinator/placement-requests/7/approve")
                        .with(user(TestUsers.coordinator(3))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectPassesNoteToService() throws Exception {
        when(service.reject(3, 7, "not now")).thenReturn(response(PlacementRequestStatus.REJECTED));

        mvc.perform(post("/api/coordinator/placement-requests/7/reject")
                        .with(user(TestUsers.coordinator(3))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"not now\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void rejectNoteIsLimitedTo500Characters() throws Exception {
        mvc.perform(post("/api/coordinator/placement-requests/7/reject")
                        .with(user(TestUsers.coordinator(3))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).reject(any(), any(), any());
    }

    private static PlacementRequestResponse response(PlacementRequestStatus status) {
        return new PlacementRequestResponse(7, 100, "Acme", "Student 5", "CHANGE",
                status, null, null, Instant.now(), null);
    }
}
