package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.ApplicationId;

import static java.util.Objects.requireNonNull;

/**
 * A claim for ownership of some application by some identity.
 *
 * @author jonmv
 */
public abstract class ApplicationClaim {

    private final ApplicationId application;

    protected ApplicationClaim(ApplicationId application) {
        this.application = requireNonNull(application);
    }

    /** The application this permit concerns. */
    public ApplicationId application() { return application; }

}
