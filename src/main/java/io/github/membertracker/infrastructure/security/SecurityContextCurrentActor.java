package io.github.membertracker.infrastructure.security;

import io.github.membertracker.domain.service.CurrentActor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reads the signed-in user's email (the username) from the Spring Security context. */
@Component
public class SecurityContextCurrentActor implements CurrentActor {

    @Override
    public String getEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        return authentication.getName();
    }
}
