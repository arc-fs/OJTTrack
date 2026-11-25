package com.ojttrack.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
import com.ojttrack.backend.placement.PlacementRepository;
import com.ojttrack.backend.placement.PlacementRequestRepository;
import com.ojttrack.backend.placement.PlacementRequestStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the web layer only: URL mapping, security rules, validation and JSON.
 * The repository is a Mockito mock, so no database is needed.
 */
@WebMvcTest(JobApplicationController.class)
@Import({SecurityConfig.class, PasswordConfig.class})
class JobApplicationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JobApplicationRepository repository;

    @MockitoBean
    PlacementRepository placements;

    @MockitoBean
    PlacementRequestRepository placementRequests;

    // ---- applications linked to placements are locked ----

    private static final String UPDATE_BODY = "{\"companyName\":\"Acme\",\"status\":\"REJECTED\"}";

    @Test
    void updateOfUnlinkedApplicationSucceeds() throws Exception {
        when(repository.findByIdForUpdate(40)).thenReturn(Optional.of(application(5, "Acme")));
        when(repository.save(any(JobApplication.class))).thenAnswer(call -> call.getArgument(0));

        mvc.perform(put("/api/applications/40")
                        .with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void updateOfApplicationWithPlacementIsAConflict() throws Exception {
        when(repository.findByIdForUpdate(40)).thenReturn(Optional.of(application(5, "Acme")));
        when(placements.existsByApplicationId(40)).thenReturn(true);

        mvc.perform(put("/api/applications/40")
                        .with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("linked to a placement")));
        verify(repository, never()).save(any());
    }

    @Test
    void deleteOfApplicationWithPendingRequestIsAConflict() throws Exception {
        when(repository.findByIdForUpdate(40)).thenReturn(Optional.of(application(5, "Acme")));
        when(placementRequests.existsByApplicationIdAndStatus(40, PlacementRequestStatus.PENDING)).thenReturn(true);

        mvc.perform(delete("/api/applications/40").with(user(TestUsers.student(5))).with(csrf()))
                .andExpect(status().isConflict());
        verify(repository, never()).delete(any());
    }

    @Test
    void updateOfAnotherStudentsApplicationIsNotFound() throws Exception {
        when(repository.findByIdForUpdate(40)).thenReturn(Optional.of(application(99, "Acme")));

        mvc.perform(put("/api/applications/40")
                        .with(user(TestUsers.student(5))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isNotFound());
        verify(repository, never()).save(any());
    }

    // ---- existing behaviour ----

    @Test
    void listRequiresLogin() throws Exception {
        mvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void coordinatorCannotAccessStudentApplications() throws Exception {
        mvc.perform(get("/api/applications").with(user(TestUsers.coordinator(1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listReturnsOnlyTheLoggedInStudentsApplications() throws Exception {
        when(repository.findByStudentId(5)).thenReturn(List.of(application(5, "Acme")));

        mvc.perform(get("/api/applications").with(user(TestUsers.student(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("Acme"))
                .andExpect(jsonPath("$[0].studentId").doesNotExist());
    }

    @Test
    void writeWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/applications")
                        .with(user(TestUsers.student(5)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Acme\",\"status\":\"PLANNED\"}"))
                .andExpect(status().isForbidden());
        verify(repository, never()).save(any());
    }

    @Test
    void createSavesApplicationForLoggedInStudent() throws Exception {
        when(repository.save(any(JobApplication.class))).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post("/api/applications")
                        .with(user(TestUsers.student(5)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"  Acme  \",\"status\":\"APPLIED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Acme"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.studentId").doesNotExist());

        ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStudentId()).isEqualTo(5);
    }

    @Test
    void blankCompanyNameIsRejected() throws Exception {
        mvc.perform(post("/api/applications")
                        .with(user(TestUsers.student(5)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"   \",\"status\":\"PLANNED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("companyName")));
        verify(repository, never()).save(any());
    }

    @Test
    void companyNameLongerThanColumnIsRejected() throws Exception {
        String tooLong = "A".repeat(201);
        mvc.perform(post("/api/applications")
                        .with(user(TestUsers.student(5)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + tooLong + "\",\"status\":\"PLANNED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("companyName")));
        verify(repository, never()).save(any());
    }

    @Test
    void anotherStudentsApplicationIsNotFound() throws Exception {
        // Application 99 exists but belongs to someone else, so the owned lookup is empty.
        when(repository.findByIdAndStudentId(99, 5)).thenReturn(Optional.empty());

        mvc.perform(get("/api/applications/99").with(user(TestUsers.student(5))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Application not found"));
    }

    private static JobApplication application(int studentId, String companyName) {
        JobApplication application = new JobApplication();
        application.setStudentId(studentId);
        application.setCompanyName(companyName);
        application.setStatus(ApplicationStatus.PLANNED);
        return application;
    }
}
