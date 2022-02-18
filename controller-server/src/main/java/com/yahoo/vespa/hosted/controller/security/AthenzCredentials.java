// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.OAuthCredentials;

import static java.util.Objects.requireNonNull;

/**
 * Like {@link Credentials}, but the entity is rather an Athenz domain, and thus contains also a
 * token which can be used to validate the user's role memberships under this domain.
 * <em>This validation is done by Athenz, not by us.</em>
 *
 * @author jonmv
 */
public class AthenzCredentials extends Credentials {

    private final AthenzDomain domain;
    private final OAuthCredentials oAuthCredentials;

    public AthenzCredentials(AthenzPrincipal user, AthenzDomain domain, OAuthCredentials oAuthCredentials) {
        super(user);
        this.domain = requireNonNull(domain);
        this.oAuthCredentials = requireNonNull(oAuthCredentials);
    }

    @Override
    public AthenzPrincipal user() { return (AthenzPrincipal) super.user(); }

    /** Returns the Athenz domain of the tenant on whose behalf this request is made. */
    public AthenzDomain domain() { return domain; }

    /** Returns the OAuth credentials required for Athenz tenancy operation */
    public OAuthCredentials oAuthCredentials() { return oAuthCredentials; }

}
