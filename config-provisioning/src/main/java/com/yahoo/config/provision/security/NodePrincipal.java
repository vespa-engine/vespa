// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.security;

import java.security.Principal;
import java.util.Objects;

/**
 * Represents the identity of a hosted Vespa node
 *
 * @author bjorncs
 */
public class NodePrincipal implements Principal {

    private final NodeIdentity identity;

    public NodePrincipal(NodeIdentity identity) {
        this.identity = identity;
    }

    public NodeIdentity getIdentity() {
        return identity;
    }

    @Override
    public String getName() {
        StringBuilder builder = new StringBuilder(identity.nodeType().name());
        identity.hostname().ifPresent(hostname -> builder.append('/').append(hostname.value()));
        identity.applicationId().ifPresent(applicationId -> builder.append('/').append(applicationId.toShortString()));
        return builder.toString();
    }

    @Override
    public String toString() {
        return "NodePrincipal{" +
                "identity=" + identity +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodePrincipal that = (NodePrincipal) o;
        return Objects.equals(identity, that.identity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity);
    }
}
