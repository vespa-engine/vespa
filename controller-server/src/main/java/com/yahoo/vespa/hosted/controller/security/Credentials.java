package com.yahoo.vespa.hosted.controller.security;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * Credentials proving an entity's access to some resource.
 *
 * @author jonmv
 */
public class Credentials<PrincipalType extends Principal> {

    private final PrincipalType user;

    public Credentials(PrincipalType user) {
        this.user = requireNonNull(user);
    }

    /** Returns the user which owns these credentials. */
    public PrincipalType user() { return user; }

}
