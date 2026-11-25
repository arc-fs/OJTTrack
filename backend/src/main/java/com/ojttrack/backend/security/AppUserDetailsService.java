package com.ojttrack.backend.security;

import com.ojttrack.backend.user.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(email.trim().toLowerCase())
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }
}