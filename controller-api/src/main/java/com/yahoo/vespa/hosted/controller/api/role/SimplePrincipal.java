// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import java.security.Principal;

/**
 * A principal wrapper of a single String entry.
 *
 * @author jonmv
 */
public class SimplePrincipal implements Principal {

    private final String name;

    public SimplePrincipal(String name) {
        if (name.isBlank())
            throw new IllegalArgumentException("Name cannot be blank");
        this.name = name;
    }

    public static SimplePrincipal of(Principal principal) {
        return new SimplePrincipal(principal.getName());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return name.equals(((SimplePrincipal) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
