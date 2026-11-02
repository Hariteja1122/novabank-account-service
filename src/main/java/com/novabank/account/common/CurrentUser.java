package com.novabank.account.common;

import org.springframework.security.oauth2.jwt.Jwt;

/** Small helper for reading the caller's identity from the validated JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    public static boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("role"));
    }
}
