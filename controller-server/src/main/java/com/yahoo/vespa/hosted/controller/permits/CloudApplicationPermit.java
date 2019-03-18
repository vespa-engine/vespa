package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.ApplicationId;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * Wraps the permit data of an Okta application modification.
 *
 * @author jonmv
 */
public class CloudApplicationPermit extends ApplicationPermit {

    private final Principal user;

    public CloudApplicationPermit(ApplicationId application, Principal user) {
        super(application);
        this.user = requireNonNull(user);
    }

    public Principal user() { return user; }

}
