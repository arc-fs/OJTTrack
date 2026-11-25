package com.ojttrack.backend.auth;

import com.ojttrack.backend.security.AppUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUserDetails user) {
        return new MeResponse(user.getId(), user.getUsername(), user.getRole().name());
    }

    /**
     * Makes Spring Security issue the XSRF-TOKEN cookie. The token is generated lazily,
     * so the frontend calls this before its first POST/PUT/DELETE (including login),
     * and again after login/logout, which rotate the token.
     */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        token.getToken(); // reading the token forces the cookie to be written
    }
}