package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.OktaAccessToken;

import static java.util.Objects.requireNonNull;

/**
 * Like {@link Credentials}, but additionally a domain and a token which proves the user's
 * access to some actions under that domain.
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

    /** Returns the Athenz domain these credentials refer to. */
    public AthenzDomain domain() { return domain; }

    /** Returns the token proving access to the requested action under this domain. */
    public OktaAccessToken token() { return token; }

}
