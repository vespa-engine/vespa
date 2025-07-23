// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import com.yahoo.vespa.athenz.utils.AthenzIdentities;

import java.net.URI;
import java.util.Objects;

/**
 * @author bjorncs
 */
public class AthenzService implements AthenzIdentity {

    private final AthenzDomain domain;
    private final String serviceName;

    public AthenzService(AthenzDomain domain, String serviceName) {
        this.domain = domain;
        this.serviceName = serviceName;
    }

    public AthenzService(String domain, String serviceName) {
        this(new AthenzDomain(domain), serviceName);
    }

    public AthenzService(String fullName) {
        AthenzIdentity identity = AthenzIdentities.from(fullName);
        if ( ! (identity instanceof AthenzService service)) {
            throw new IllegalArgumentException(String.format("'%s' is not an Athenz service", fullName));
        }
        this.domain = service.domain;
        this.serviceName = service.serviceName;
    }

    public AthenzResourceName toResourceName() {
        return new AthenzResourceName(domain, "service." + serviceName);
    }

    @Override
    public AthenzDomain getDomain() {
        return domain;
    }

    @Override
    public String getName() {
        return serviceName;
    }

    @Override
    public URI spiffeUri() {
        // spiffe://athenz.cloud/ns/default/sa/<athenz-domain>.<athenz-service>
        return URI.create("spiffe://athenz.cloud/ns/default/sa/%s.%s".formatted(getDomainName(), serviceName));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzService that = (AthenzService) o;
        return Objects.equals(domain, that.domain) &&
                Objects.equals(serviceName, that.serviceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, serviceName);
    }

    @Override
    public String toString() {
        return String.format("AthenzService(%s)", getFullName());
    }
}
