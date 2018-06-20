// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Collections.emptyList;

/**
 * @author bjorncs
 */
public class AthenzPrincipal implements Principal {

    private final AthenzIdentity athenzIdentity;
    private final NToken nToken;
    private final List<AthenzRole> roles;

    public AthenzPrincipal(AthenzIdentity athenzIdentity) {
        this(athenzIdentity, null, emptyList());
    }

    public AthenzPrincipal(AthenzIdentity athenzIdentity, NToken nToken) {
        this(athenzIdentity, nToken, emptyList());
    }

    public AthenzPrincipal(AthenzIdentity identity, List<AthenzRole> roles) {
        this(identity, null, roles);
    }

    private AthenzPrincipal(AthenzIdentity athenzIdentity, NToken nToken, List<AthenzRole> roles) {
        this.athenzIdentity = athenzIdentity;
        this.nToken = nToken;
        this.roles = roles;
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

    public List<AthenzRole> getRoles() {
        return roles;
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
