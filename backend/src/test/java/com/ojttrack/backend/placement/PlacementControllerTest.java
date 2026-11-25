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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Student placement endpoints: security, validation and JSON. Business rules are in PlacementServiceTest. */
@WebMvcTest(PlacementController.class)
@Import({SecurityConfig.class, PasswordConfig.class})
class PlacementControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlacementService service;

    @Test
    void placementsRequireLogin() throws Exception {
        mvc.perform(get("/api/placements")).andExpect(status().isUnauthorized());
    }

    @Test
    void coordinatorCannotUseStudentPlacementEndpoints() throws Exception {
        mvc.perform(get("/api/placements").with(user(TestUsers.coordinator(1))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/placement-requests").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationId\":100}"))
                .andExpect(status().isForbidden());
        verify(service, never()).submit(any(), any(), any());
    }

    @Test
    void listsOnlyTheLoggedInStudentsPlacements() throws Exception {
        when(service.placementsFor(5)).thenReturn(List.of(new PlacementResponse(
                500, 100, "Acme", PlacementStatus.ACTIVE, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 4, 30),
                486, Instant.now(), null)));

        mvc.perform(get("/api/placements").with(user(TestUsers.student(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("Acme"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].studentId").doesNotExist());
        verify(service).placementsFor(5);
    }

    @Test
    void submitCreatesRequestForLoggedInStudent() throws Exception {
        when(service.submit(eq(5), eq(100), eq("please"))).thenReturn(requestResponse());

        mvc.perform(post("/api/placement-requests").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":100,\"reason\":\"please\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ACTIVATION"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void submitWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/placement-requests").with(user(TestUsers.student(5)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationId\":100}"))
                .andExpect(status().isForbidden());
        verify(service, never()).submit(any(), any(), any());
    }

    @Test
    void submitValidatesBody() throws Exception {
        mvc.perform(post("/api/placement-requests").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/placement-requests").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":100,\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).submit(any(), any(), any());
    }

    @Test
    void serviceConflictBecomes409WithMessage() throws Exception {
        when(service.submit(any(), any(), any())).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Only ACCEPTED applications can become placements"));

        mvc.perform(post("/api/placement-requests").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationId\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only ACCEPTED applications can become placements"));
    }

    private static PlacementRequestResponse requestResponse() {
        return new PlacementRequestResponse(7, 100, "Acme", "Student 5", "ACTIVATION",
                PlacementRequestStatus.PENDING, "please", null, Instant.now(), null);
    }
}
