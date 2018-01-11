// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzDomain;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

/**
 * @author bjorncs
 */
public class AthenzPrincipal implements Principal {

    private final AthenzIdentity athenzIdentity;
    private final NToken nToken;

    public AthenzPrincipal(AthenzIdentity athenzIdentity) {
        this(athenzIdentity, null);
    }

    public AthenzPrincipal(AthenzIdentity athenzIdentity,
                           NToken nToken) {
        this.athenzIdentity = athenzIdentity;
        this.nToken = nToken;
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

    public Optional<NToken> getNToken() {
        return Optional.ofNullable(nToken);
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
