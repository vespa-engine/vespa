// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.api.OktaIdentityToken;

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
    private final OktaIdentityToken identityToken;
    private final OktaAccessToken accessToken;

    public AthenzCredentials(AthenzPrincipal user, AthenzDomain domain,
                             OktaIdentityToken identityToken, OktaAccessToken accessToken) {
        super(user);
        this.domain = requireNonNull(domain);
        this.accessToken = requireNonNull(accessToken);
        this.identityToken = requireNonNull(identityToken);
    }

    @Override
    public AthenzPrincipal user() { return (AthenzPrincipal) super.user(); }

    /** Returns the Athenz domain of the tenant on whose behalf this request is made. */
    public AthenzDomain domain() { return domain; }

    /** Returns the Okta access token required for Athenz tenancy operation */
    public OktaAccessToken accessToken() { return accessToken; }

    /**     /** Returns the Okta identity token required for Athenz tenancy operation */
    public OktaIdentityToken identityToken() { return identityToken; }


}
