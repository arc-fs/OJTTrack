package com.ojttrack.backend.application;

import com.ojttrack.backend.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    private final JobApplicationRepository repository;

    public JobApplicationController(JobApplicationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<JobApplication> list(@AuthenticationPrincipal AppUserDetails user) {
        return repository.findByStudentId(user.getId());
    }

    @GetMapping("/{id}")
    public JobApplication get(@PathVariable Integer id, @AuthenticationPrincipal AppUserDetails user) {
        return findOwned(id, user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobApplication create(@Valid @RequestBody CreateApplicationRequest request,
                                 @AuthenticationPrincipal AppUserDetails user) {
        JobApplication application = new JobApplication();
        application.setStudentId(user.getId());
        application.setCompanyName(request.companyName());
        application.setStatus(request.status());
        return repository.save(application);
    }

    @PutMapping("/{id}")
    public JobApplication update(@PathVariable Integer id,
                                 @Valid @RequestBody CreateApplicationRequest request,
                                 @AuthenticationPrincipal AppUserDetails user) {
        JobApplication application = findOwned(id, user.getId());
        application.setCompanyName(request.companyName());
        application.setStatus(request.status());
        return repository.save(application);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, @AuthenticationPrincipal AppUserDetails user) {
        JobApplication application = findOwned(id, user.getId());
        repository.delete(application);
    }

    private JobApplication findOwned(Integer id, Integer studentId) {
        return repository.findByIdAndStudentId(id, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }
}