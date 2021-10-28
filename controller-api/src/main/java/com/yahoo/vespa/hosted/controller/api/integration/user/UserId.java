// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.user;

import java.util.Objects;

/**
 * An identifier for a user.
 *
 * @author jonmv
 */
public class UserId {

    private final String value;

    public UserId(String value) {
        if (value.isBlank())
            throw new IllegalArgumentException("Id must be non-blank.");
        this.value = value;
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserId id = (UserId) o;
        return Objects.equals(value, id.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "user '" + value + "'";
    }

}
