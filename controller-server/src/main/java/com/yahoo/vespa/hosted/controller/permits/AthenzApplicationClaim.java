package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.OktaAccessToken;

import static java.util.Objects.requireNonNull;

/**
 * Wraps the claim data of an Athenz application modification.
 *
 * @author jonmv
 */
public class AthenzApplicationClaim extends ApplicationClaim {

    private final AthenzDomain domain;
    private final OktaAccessToken token;

    public AthenzApplicationClaim(ApplicationId application, AthenzDomain domain, OktaAccessToken token) {
        super(application);
        this.domain = requireNonNull(domain);
        this.token = requireNonNull(token);
    }

    /** The athenz domain to create this application under. */
    public AthenzDomain domain() { return domain; }

     /** The Okta issued token proving the user's access to Athenz. */
    public OktaAccessToken token() { return token; }

}
