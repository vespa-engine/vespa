// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import com.yahoo.athenz.auth.token.RoleToken;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * Represents an Athenz ZToken (role token)
 *
 * @author bjorncs
 */
public class ZToken {

    private final RoleToken token;

    public ZToken(String rawToken) {
        this.token = new RoleToken(rawToken);
    }

    public String getRawToken() {
        return token.getSignedToken();
    }

    public AthenzIdentity getIdentity() {
        return AthenzIdentities.from(token.getPrincipal());
    }

    public List<AthenzRole> getRoles() {
        String domain = token.getDomain();
        return token.getRoles().stream()
                .map(roleName -> new AthenzRole(domain, roleName))
                .collect(toList());}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZToken zToken = (ZToken) o;
        return Objects.equals(getRawToken(), zToken.getRawToken());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRawToken());
    }


}
