// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

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
