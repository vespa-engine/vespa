package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.ApplicationId;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * Wraps the permit data of an Okta application modification.
 */
public class CloudApplicationPermit {

    private final ApplicationId application;
    private final Principal user;

    public CloudApplicationPermit(ApplicationId application, Principal user) {
        this.application = requireNonNull(application);
        this.user = requireNonNull(user);
    }

    public ApplicationId application() { return application; }
    public Principal user() { return user; }

}
