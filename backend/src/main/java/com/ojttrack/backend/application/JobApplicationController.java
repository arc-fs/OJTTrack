package com.ojttrack.backend.application;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    // TEMPORARY: replaced by the logged-in user's id when authentication is added
    private static final int DEV_STUDENT_ID = 1;

    private final JobApplicationRepository repository;

    public JobApplicationController(JobApplicationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<JobApplication> list() {
        return repository.findByStudentId(DEV_STUDENT_ID);
    }
    @GetMapping("/{id}")
    public JobApplication get(@PathVariable Integer id) {
            return findOwned(id);
    }
    private JobApplication findOwned(Integer id) {
        return repository.findByIdAndStudentId(id, DEV_STUDENT_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobApplication create(@Valid @RequestBody CreateApplicationRequest request) {
        JobApplication application = new JobApplication();
        application.setStudentId(DEV_STUDENT_ID);
        application.setCompanyName(request.companyName());
        application.setStatus(request.status());
        return repository.save(application);
    }

    @PutMapping("/{id}")
    public JobApplication update(@PathVariable Integer id,
                                 @Valid @RequestBody CreateApplicationRequest request) {
        JobApplication application = findOwned(id);
        application.setCompanyName(request.companyName());
        application.setStatus(request.status());
        return repository.save(application);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        JobApplication application = findOwned(id);
        repository.delete(application);
    }
}