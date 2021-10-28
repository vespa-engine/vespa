// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * Credentials representing an entity for which to modify access control rules.
 *
 * @author jonmv
 */
public class Credentials {

    private final Principal user;

    public Credentials(Principal user) {
        this.user = requireNonNull(user);
    }

    /** Returns the user which makes the request. */
    public Principal user() { return user; }

}
