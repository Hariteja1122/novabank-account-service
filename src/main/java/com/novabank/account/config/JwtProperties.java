package com.novabank.account.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Same secret and issuer as auth-service, which signs the tokens this service validates. */
@Validated
@ConfigurationProperties(prefix = "novabank.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
        @NotBlank String issuer) {
}
