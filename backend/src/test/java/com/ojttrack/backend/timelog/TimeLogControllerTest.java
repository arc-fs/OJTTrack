package com.ojttrack.backend.timelog;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

/** Student time log endpoints: security, request validation and JSON. Rules are in TimeLogServiceTest. */
@WebMvcTest(TimeLogController.class)
@Import({SecurityConfig.class, PasswordConfig.class})
class TimeLogControllerTest {

    private static final String VALID = """
            {"placementId":500,"logDate":"2027-01-06","timeIn":"08:00","timeOut":"17:00","endsNextDay":false,
             "breakMinutes":60,"accomplishments":"Built the login page"}
            """;

    /** An edit body: no placementId (edits never move a log), but the version the student saw. */
    private static final String VALID_EDIT = VALID.replace("\"placementId\":500", "\"version\":2");

    @Test
    void createRequiresPlacementId() throws Exception {
        String body = VALID.replace("\"placementId\":500,", "");

        mvc.perform(post("/api/time-logs").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("placementId")));
        verify(service, never()).submit(any(), any());
    }

    @Test
    void editRequiresVersion() throws Exception {
        String body = VALID_EDIT.replace("\"version\":2,", "");

        mvc.perform(put("/api/time-logs/7").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("version")));
        verify(service, never()).edit(any(), any(), any());
    }

    @Test
    void editTimesWithSecondsAreRejected() throws Exception {
        // The whole-minute rule lives in the shared TimeLogFields interface: check it applies to edits too.
        String body = VALID_EDIT.replace("\"17:00\"", "\"17:00:15\"");

        mvc.perform(put("/api/time-logs/7").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("whole minutes")));
        verify(service, never()).edit(any(), any(), any());
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TimeLogService service;

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/time-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void coordinatorCannotUseStudentLogEndpoints() throws Exception {
        mvc.perform(get("/api/time-logs").with(user(TestUsers.coordinator(1))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/time-logs").with(user(TestUsers.coordinator(1))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        verify(service, never()).submit(any(), any());
    }

    @Test
    void submitWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/time-logs").with(user(TestUsers.student(5)))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        verify(service, never()).submit(any(), any());
    }

    @Test
    void submitUsesTheLoggedInStudent() throws Exception {
        when(service.submit(eq(5), any())).thenReturn(response());

        mvc.perform(post("/api/time-logs").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workedMinutes").value(480))
                .andExpect(jsonPath("$.timeIn").value("08:00:00"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.studentId").doesNotExist());
    }

    @Test
    void timesWithSecondsAreRejected() throws Exception {
        String body = VALID.replace("\"08:00\"", "\"08:00:30\"");

        mvc.perform(post("/api/time-logs").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("whole minutes")));
        verify(service, never()).submit(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"timeIn\":\"08:00\",\"timeOut\":\"17:00\",\"breakMinutes\":60,\"accomplishments\":\"x\"}",            // no date
            "{\"logDate\":\"2027-01-06\",\"timeOut\":\"17:00\",\"breakMinutes\":60,\"accomplishments\":\"x\"}",       // no time in
            "{\"logDate\":\"2027-01-06\",\"timeIn\":\"08:00\",\"timeOut\":\"17:00\",\"accomplishments\":\"x\"}",      // no break
            "{\"logDate\":\"2027-01-06\",\"timeIn\":\"08:00\",\"timeOut\":\"17:00\",\"breakMinutes\":-1,\"accomplishments\":\"x\"}",
            "{\"logDate\":\"2027-01-06\",\"timeIn\":\"08:00\",\"timeOut\":\"17:00\",\"breakMinutes\":60,\"accomplishments\":\"   \"}",
            "{\"logDate\":\"2027-01-06\",\"timeIn\":\"25:00\",\"timeOut\":\"17:00\",\"breakMinutes\":60,\"accomplishments\":\"x\"}"
    })
    void invalidBodiesAreRejected(String body) throws Exception {
        mvc.perform(post("/api/time-logs").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(service, never()).submit(any(), any());
    }

    @Test
    void accomplishmentsAreLimitedTo2000Characters() throws Exception {
        String body = VALID.replace("Built the login page", "x".repeat(2001));

        mvc.perform(post("/api/time-logs").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editPassesIdAndBodyToService() throws Exception {
        when(service.edit(eq(5), eq(7), any())).thenReturn(response());
        String body = VALID_EDIT;

        mvc.perform(put("/api/time-logs/7").with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).edit(eq(5), eq(7), any());
    }

    @Test
    void listPassesOptionalPlacementId() throws Exception {
        when(service.list(5, null)).thenReturn(List.of(response()));

        mvc.perform(get("/api/time-logs").with(user(TestUsers.student(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accomplishments").value("Built the login page"));
        verify(service).list(eq(5), isNull());
    }

    private static TimeLogResponse response() {
        return new TimeLogResponse(7, 500, LocalDate.of(2027, 1, 6), LocalTime.of(8, 0), LocalTime.of(17, 0),
                false, 60, 480, "Built the login page", TimeLogStatus.PENDING, null, null, null, 0,
                Instant.now(), Instant.now());
    }
}
