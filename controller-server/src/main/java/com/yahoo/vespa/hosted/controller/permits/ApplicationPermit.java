package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.ApplicationId;

import static java.util.Objects.requireNonNull;

/**
 * Data that relates identities to permissions to an application.
 *
 * @author jonmv
 */
public abstract class ApplicationPermit {

    private final ApplicationId application;

    protected ApplicationPermit(ApplicationId application) {
        this.application = requireNonNull(application);
    }

    /** The application this permit concerns. */
    public ApplicationId application() { return application; }

}

