// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;

import java.security.Principal;
import java.util.Objects;

/**
 * @author bjorncs
 */
public class AthenzPrincipal implements Principal {

    private final AthenzIdentity athenzIdentity;

    public AthenzPrincipal(AthenzIdentity athenzIdentity) {
        this.athenzIdentity = athenzIdentity;
    }

    public AthenzIdentity getIdentity() {
        return athenzIdentity;
    }

    @Override
    public String getName() {
        return athenzIdentity.getFullName();
    }

    public AthenzDomain getDomain() {
        return athenzIdentity.getDomain();
    }

    @Override
    public String toString() {
        return "AthenzPrincipal{" +
                "athenzIdentity=" + athenzIdentity +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzPrincipal principal = (AthenzPrincipal) o;
        return Objects.equals(athenzIdentity, principal.athenzIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(athenzIdentity);
    }
}
