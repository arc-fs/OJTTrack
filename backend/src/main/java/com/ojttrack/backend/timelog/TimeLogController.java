package com.ojttrack.backend.timelog;

import com.ojttrack.backend.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Student time log endpoints (SecurityConfig: STUDENT only). The student id always comes from the session. */
@RestController
@RequestMapping("/api/time-logs")
public class TimeLogController {

    private final TimeLogService service;

    public TimeLogController(TimeLogService service) {
        this.service = service;
    }

    @GetMapping
    public List<TimeLogResponse> list(@RequestParam(required = false) Integer placementId,
                                      @AuthenticationPrincipal AppUserDetails student) {
        return service.list(student.getId(), placementId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimeLogResponse submit(@Valid @RequestBody TimeLogRequest body,
                                  @AuthenticationPrincipal AppUserDetails student) {
        return service.submit(student.getId(), body);
    }

    @PutMapping("/{id}")
    public TimeLogResponse edit(@PathVariable Integer id,
                                @Valid @RequestBody TimeLogRequest body,
                                @AuthenticationPrincipal AppUserDetails student) {
        return service.edit(student.getId(), id, body);
    }
}
