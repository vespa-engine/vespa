package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.ApplicationId;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * Wraps the claim data of an Okta application modification.
 *
 * @author jonmv
 */
public class CloudApplicationClaim extends ApplicationClaim {

    private final Principal user;

    public CloudApplicationClaim(ApplicationId application, Principal user) {
        super(application);
        this.user = requireNonNull(user);
    }

    public Principal user() { return user; }

}
