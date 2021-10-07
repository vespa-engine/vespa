// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.vespa.hosted.controller.api.role.Role;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 * Like {@link Credentials}, but we know the principal is authenticated by Auth0.
 * Also includes the set of roles for which the principal is a member.
 *
 * @author andreer
 */
public class Auth0Credentials extends Credentials {

    private final Set<Role> roles;

    public Auth0Credentials(Principal user, Set<Role> roles) {
        super(user);
        this.roles = Collections.unmodifiableSet(roles);
    }

    /** The set of roles set in the auth0 cookie, extracted by CloudAccessControlRequests. */
    public Set<Role> getRolesFromCookie() {
        return roles;
    }

}
