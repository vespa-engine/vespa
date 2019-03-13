package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.ApplicationId;

import java.security.Principal;
import java.util.Objects;

/**
 * Wraps the permit data of an Okta application modification.
 */
public class OktaApplicationPermit {

    private final ApplicationId application;
    private final Principal user;

    public OktaApplicationPermit(ApplicationId application, Principal user) {
        this.application = Objects.requireNonNull(application);
        this.user = Objects.requireNonNull(user);
    }

    public ApplicationId application() { return application; }
    public Principal user() { return user; }

}
