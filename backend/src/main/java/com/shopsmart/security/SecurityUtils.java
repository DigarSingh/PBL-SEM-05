package com.shopsmart.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Access to the current caller for code that cannot use {@link CurrentUser} injection. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.of(AuthUser.fromJwt(jwtAuthentication.getToken()));
        }
        return Optional.empty();
    }

    public static Optional<Long> currentUserId() {
        return currentUser().map(AuthUser::id);
    }
}
