package com.ojttrack.backend.application;

import com.ojttrack.backend.placement.PlacementRepository;
import com.ojttrack.backend.placement.PlacementRequestRepository;
import com.ojttrack.backend.placement.PlacementRequestStatus;
import com.ojttrack.backend.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    private final JobApplicationRepository repository;
    private final PlacementRepository placements;
    private final PlacementRequestRepository placementRequests;

    public JobApplicationController(JobApplicationRepository repository,
                                    PlacementRepository placements,
                                    PlacementRequestRepository placementRequests) {
        this.repository = repository;
        this.placements = placements;
        this.placementRequests = placementRequests;
    }

    @GetMapping
    public List<ApplicationResponse> list(@AuthenticationPrincipal AppUserDetails user) {
        return repository.findByStudentId(user.getId()).stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable Integer id, @AuthenticationPrincipal AppUserDetails user) {
        return ApplicationResponse.from(findOwned(id, user.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody CreateApplicationRequest request,
                                      @AuthenticationPrincipal AppUserDetails user) {
        JobApplication application = new JobApplication();
        application.setStudentId(user.getId());
        application.setCompanyName(request.companyName().trim());
        application.setStatus(request.status());
        return ApplicationResponse.from(repository.save(application));
    }

    // update and delete lock the row and run in one transaction, so a coordinator approving
    // a placement for this application at the same moment can't interleave with the edit.
    @PutMapping("/{id}")
    @Transactional
    public ApplicationResponse update(@PathVariable Integer id,
                                      @Valid @RequestBody CreateApplicationRequest request,
                                      @AuthenticationPrincipal AppUserDetails user) {
        JobApplication application = findOwnedEditable(id, user.getId());
        application.setCompanyName(request.companyName().trim());
        application.setStatus(request.status());
        return ApplicationResponse.from(repository.save(application));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Integer id, @AuthenticationPrincipal AppUserDetails user) {
        JobApplication application = findOwnedEditable(id, user.getId());
        repository.delete(application);
    }

    private JobApplication findOwned(Integer id, Integer studentId) {
        return repository.findByIdAndStudentId(id, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    /**
     * Owned by the student AND not tied to a placement or a pending placement request.
     * Otherwise a student could un-accept or delete the application behind their placement.
     */
    private JobApplication findOwnedEditable(Integer id, Integer studentId) {
        JobApplication application = repository.findByIdForUpdate(id)
                .filter(app -> app.getStudentId().equals(studentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (placements.existsByApplicationId(id)
                || placementRequests.existsByApplicationIdAndStatus(id, PlacementRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This application is linked to a placement and can no longer be changed");
        }
        return application;
    }
}