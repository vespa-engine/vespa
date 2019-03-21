package com.yahoo.vespa.hosted.controller.security;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * Credentials proving an entity's access to some resource.
 *
 * @author jonmv
 */
public class Credentials {

    private final Principal user;

    public Credentials(Principal user) {
        this.user = requireNonNull(user);
    }

    /** Returns the user which owns these credentials. */
    public Principal user() { return user; }

}
