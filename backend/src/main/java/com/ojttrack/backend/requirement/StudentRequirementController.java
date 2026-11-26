package com.ojttrack.backend.requirement;

import com.ojttrack.backend.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Student requirement endpoints (SecurityConfig: STUDENT only). The student id comes from the session. */
@RestController
@RequestMapping("/api/requirements")
public class StudentRequirementController {

    private final StudentRequirementService service;

    public StudentRequirementController(StudentRequirementService service) {
        this.service = service;
    }

    @GetMapping
    public List<StudentRequirementResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived,
                                                 @AuthenticationPrincipal AppUserDetails student) {
        return service.list(student.getId(), includeArchived);
    }

    @PostMapping("/{assignmentId}/submit")
    public StudentRequirementResponse submit(@PathVariable Integer assignmentId,
                                             @Valid @RequestBody SubmitRequirementRequest body,
                                             @AuthenticationPrincipal AppUserDetails student) {
        return service.submit(student.getId(), assignmentId, body.version(), body.note());
    }
}
