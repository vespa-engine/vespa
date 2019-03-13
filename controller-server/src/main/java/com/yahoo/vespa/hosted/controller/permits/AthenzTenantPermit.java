package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.OktaAccessToken;

import java.util.Objects;

/**
 * Wraps the permit data of an Athenz tenancy modification.
 *
 * @author jonmv
 */
public class AthenzTenantPermit implements TenantPermit {

    private final AthenzDomain domain;
    private final OktaAccessToken token;

    public AthenzTenantPermit(AthenzDomain domain, OktaAccessToken token) {
        this.domain = Objects.requireNonNull(domain);
        this.token = Objects.requireNonNull(token);
    }

    public AthenzDomain domain() { return domain; }
    public OktaAccessToken token() { return token; }

}
