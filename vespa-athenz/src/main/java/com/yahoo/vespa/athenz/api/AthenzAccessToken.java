// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an Athenz Access Token
 *
 * @author bjorncs
 */
public class AthenzAccessToken {

    public static final String HTTP_HEADER_NAME = "Authorization";

    private static final String BEARER_TOKEN_PREFIX = "Bearer ";

    private final String value;
    private volatile DecodedJWT jwt;

    public AthenzAccessToken(String value) {
        this.value = stripBearerTokenPrefix(value);
    }

    private static String stripBearerTokenPrefix(String rawValue) {
        String stripped = rawValue.strip();
        String prefixRemoved = stripped.startsWith(BEARER_TOKEN_PREFIX)
                ? stripped.substring(BEARER_TOKEN_PREFIX.length()).strip()
                : stripped;
        if (prefixRemoved.isBlank()) {
            throw new IllegalArgumentException(String.format("Access token is blank: '%s'", prefixRemoved));
        }
        return prefixRemoved;
    }

    public String value() { return value; }
    public String valueWithBearerPrefix() { return BEARER_TOKEN_PREFIX + value; }
    public Instant getExpiryTime () {
        return jwt().getExpiresAt().toInstant();
    }
    public AthenzIdentity getAthenzIdentity() { return AthenzIdentities.from(jwt().getClaim("client_id").asString()); }

    private DecodedJWT jwt() {
        if (jwt == null) {
            // Decoding a token is expensive and involves construction of at least one Jackson ObjectMapper instance
            // TODO Cache encoder/decoder as static field in AthenzAccessToken
            jwt = JWT.decode(this.value);
        }
        return jwt;
    }

    @Override public String toString() { return "AthenzAccessToken{value='" + value + "'}"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzAccessToken that = (AthenzAccessToken) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
