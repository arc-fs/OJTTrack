package com.ojttrack.backend.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ojttrack.backend.TestUsers;
import com.ojttrack.backend.application.ApplicationStatus;
import com.ojttrack.backend.config.PasswordConfig;
import com.ojttrack.backend.config.SecurityConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Dashboard endpoints: who may call them and the JSON shape. Content rules are in the service/rules tests. */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, PasswordConfig.class})
class DashboardControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DashboardService service;

    @Test
    void bothDashboardsRequireLogin() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/coordinator/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void eachRoleOnlyReachesItsOwnDashboard() throws Exception {
        mvc.perform(get("/api/dashboard").with(user(TestUsers.coordinator(1)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/coordinator/dashboard").with(user(TestUsers.student(5)))).andExpect(status().isForbidden());
        verify(service, never()).studentDashboard(any());
        verify(service, never()).coordinatorDashboard(any());
    }

    @Test
    void studentDashboardUsesTheLoggedInStudent() throws Exception {
        when(service.studentDashboard(5)).thenReturn(new StudentDashboardResponse(
                Map.of(ApplicationStatus.APPLIED, 2L),
                List.of(new StudentDashboardResponse.FollowUp(3, "Acme", ApplicationStatus.APPLIED,
                        LocalDate.of(2027, 1, 18), true)),
                null, null, new StudentDashboardResponse.Requirements(List.of(), List.of(), List.of())));

        mvc.perform(get("/api/dashboard").with(user(TestUsers.student(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationCounts.APPLIED").value(2))
                .andExpect(jsonPath("$.followUps[0].overdue").value(true))
                .andExpect(jsonPath("$.placement").doesNotExist());
    }

    @Test
    void coordinatorDashboardUsesTheLoggedInCoordinator() throws Exception {
        when(service.coordinatorDashboard(3)).thenReturn(new CoordinatorDashboardResponse(
                new CoordinatorDashboardResponse.Totals(1, 0, 0, 0, 0),
                List.of(new CoordinatorDashboardResponse.StudentRow(10, "Ana", "ana@test.local",
                        LocalDate.of(2027, 1, 10), null,
                        List.of(new AssistanceFlag(AssistanceFlag.Code.NO_PLACEMENT, "No placement yet (search started 2027-01-10)")),
                        List.of()))));

        mvc.perform(get("/api/coordinator/dashboard").with(user(TestUsers.coordinator(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.students").value(1))
                .andExpect(jsonPath("$.students[0].flags[0].code").value("NO_PLACEMENT"))
                .andExpect(jsonPath("$.students[0].passwordHash").doesNotExist());
    }
}
