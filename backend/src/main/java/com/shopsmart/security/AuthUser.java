package com.shopsmart.security;

import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, derived from a validated access token. */
public record AuthUser(Long id, String email, String name, Set<String> roles) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    public boolean isAdmin() {
        return roles.contains(ROLE_ADMIN);
    }

    public static AuthUser fromJwt(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtTokenService.CLAIM_ROLES);
        return new AuthUser(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString(JwtTokenService.CLAIM_EMAIL),
                jwt.getClaimAsString(JwtTokenService.CLAIM_NAME),
                roles == null ? Set.of() : Set.copyOf(roles));
    }
}
