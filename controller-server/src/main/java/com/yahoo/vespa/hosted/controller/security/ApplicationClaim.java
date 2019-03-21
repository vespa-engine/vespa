package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.ApplicationId;

import static java.util.Objects.requireNonNull;

/**
 * A claim for ownership of some application by some identity.
 *
 * @author jonmv
 */
public class ApplicationClaim {

    private final ApplicationId application;

    public ApplicationClaim(ApplicationId application) {
        this.application = requireNonNull(application);
    }

    /** The application this permit concerns. */
    public ApplicationId application() { return application; }

}
