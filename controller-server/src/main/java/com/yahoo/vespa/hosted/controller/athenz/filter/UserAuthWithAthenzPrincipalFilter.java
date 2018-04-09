// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.cors.CorsFilterConfig;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsKeystore;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;
import com.yahoo.yolean.chain.After;

import java.security.Principal;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.stream.Stream;


/**
 * A variant of the {@link AthenzPrincipalFilter} to be used in combination with a cookie-based
 * security filter for user authentication
 * Assumes that the user authentication filter configured in the same filter chain and is configured to run before this filter.
 *
 * @author bjorncs
 */
// TODO Remove this filter once migrated to Okta
@After({"CorsPreflightRequestFilter", "BouncerFilter"})
public class UserAuthWithAthenzPrincipalFilter extends AthenzPrincipalFilter {

    private static final Logger log = Logger.getLogger(UserAuthWithAthenzPrincipalFilter.class.getName());

    private final String userAuthenticationPassThruAttribute;
    private final String principalHeaderName;

    @Inject
    public UserAuthWithAthenzPrincipalFilter(ZmsKeystore zmsKeystore,
                                             Executor executor,
                                             AthenzConfig athenzConfig,
                                             CorsFilterConfig corsConfig) {
        super(zmsKeystore, executor, athenzConfig, corsConfig);
        this.userAuthenticationPassThruAttribute = athenzConfig.userAuthenticationPassThruAttribute();
        this.principalHeaderName = athenzConfig.principalHeaderName();
    }

    @Override
    public Optional<ErrorResponse> filter(DiscFilterRequest request) {
        if (request.getMethod().equals("OPTIONS")) return Optional.empty(); // Skip authentication on OPTIONS - required for Javascript CORS

        try {
            switch (getUserAuthenticationResult(request)) {
                case USER_COOKIE_MISSING:
                case USER_COOKIE_ALTERNATIVE_MISSING:
                    return super.filter(request); // Cookie-based authentication failed, delegate to Athenz
                case USER_COOKIE_OK:
                    rewriteUserPrincipalToAthenz(request);
                    return Optional.empty(); // Authenticated using user cookie
                case USER_COOKIE_INVALID:
                    return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, "Your user cookie is invalid (either expired, tampered or invalid ip)"));
                default:
                    return Optional.empty();
            }
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Authentication failed: " + e.getMessage(), e);
            return Optional.of(new ErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()));
        }
    }

    private UserAuthenticationResult getUserAuthenticationResult(DiscFilterRequest request) {
        if (!request.containsAttribute(userAuthenticationPassThruAttribute)) {
            throw new IllegalStateException("User authentication filter passthru attribute missing");
        }
        Integer statusCode = (Integer) request.getAttribute(userAuthenticationPassThruAttribute);
        return Stream.of(UserAuthenticationResult.values())
                .filter(uar -> uar.statusCode == statusCode)
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Invalid status code: " + statusCode));
    }

    private void rewriteUserPrincipalToAthenz(DiscFilterRequest request) {
        Principal userPrincipal = request.getUserPrincipal();
        log.log(LogLevel.DEBUG, () -> "Original user principal: " + userPrincipal.toString());
        UserId userId = new UserId(userPrincipal.getName());
        AthenzUser athenzIdentity = AthenzUser.fromUserId(userId.id());
        request.setRemoteUser(athenzIdentity.getFullName());
        NToken nToken = Optional.ofNullable(request.getHeader(principalHeaderName)).map(NToken::new).orElse(null);
        request.setUserPrincipal(new AthenzPrincipal(athenzIdentity, nToken));
    }

    private enum UserAuthenticationResult {
        USER_COOKIE_MISSING(0),
        USER_COOKIE_OK(1),
        USER_COOKIE_INVALID(-1),
        USER_COOKIE_ALTERNATIVE_MISSING(-2);

        final int statusCode;

        UserAuthenticationResult(int statusCode) {
            this.statusCode = statusCode;
        }

    }
}
