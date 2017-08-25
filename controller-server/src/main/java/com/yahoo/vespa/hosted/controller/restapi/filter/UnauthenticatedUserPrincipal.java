// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import java.security.Principal;
import java.util.Objects;

/**
 * A principal for an unauthenticated user (typically from a trusted host).
 * This principal should only be used in combination with machine authentication!
 *
 * @author bjorncs
 */
public class UnauthenticatedUserPrincipal implements Principal {
    private final String username;

    public UnauthenticatedUserPrincipal(String username) {
        this.username = username;
    }

    @Override
    public String getName() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnauthenticatedUserPrincipal that = (UnauthenticatedUserPrincipal) o;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "UnauthenticatedUserPrincipal{" +
                "username='" + username + '\'' +
                '}';
    }
}
