package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.OktaAccessToken;

import java.util.Optional;

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
    private final OktaAccessToken token;

    public AthenzCredentials(AthenzPrincipal user, AthenzDomain domain, OktaAccessToken token) {
        super(user);
        this.domain = requireNonNull(domain);
        this.token = requireNonNull(token);
    }

    @Override
    public AthenzPrincipal user() { return (AthenzPrincipal) super.user(); }

    /** Returns the Athenz domain of the tenant on whose behalf this request is made. */
    public AthenzDomain domain() { return domain; }

    /** Returns the token proving access to the requested action under this domain. */
    public OktaAccessToken token() { return token; }

}
