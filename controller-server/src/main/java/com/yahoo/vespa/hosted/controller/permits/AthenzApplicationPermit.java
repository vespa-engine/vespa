package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.OktaAccessToken;

import java.util.Objects;

/**
 * Wraps the permit data of an Athenz application modification.
 *
 * @author jonmv
 */
public class AthenzApplicationPermit implements ApplicationPermit {

    private final AthenzDomain domain;
    private final ApplicationId application;
    private final OktaAccessToken token;

    public AthenzApplicationPermit(AthenzDomain domain, ApplicationId application, OktaAccessToken token) {
        this.domain = Objects.requireNonNull(domain);
        this.application = Objects.requireNonNull(application);
        this.token = Objects.requireNonNull(token);
    }

    public AthenzDomain domain() { return domain; }
    public ApplicationId application() { return application; }
    public OktaAccessToken token() { return token; }

}
