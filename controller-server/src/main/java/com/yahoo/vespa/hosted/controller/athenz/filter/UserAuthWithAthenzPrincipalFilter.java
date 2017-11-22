// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.AthenzUtils;
import com.yahoo.vespa.hosted.controller.athenz.ZmsKeystore;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;
import com.yahoo.vespa.hosted.controller.restapi.application.Authorizer;
import com.yahoo.yolean.chain.Provides;

import java.security.Principal;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.athenz.filter.SecurityFilterUtils.sendUnauthorized;

/**
 * A variant of the {@link AthenzPrincipalFilter} to be used in combination with a cookie-based
 * security filter for user authentication
 * Assumes that the user authentication filter configured in the same filter chain and is configured to run before this filter.
 *
 * @author bjorncs
 */
@Provides("UserAuthWithAthenzPrincipalFilter")
// TODO Remove this filter once migrated to Okta
public class UserAuthWithAthenzPrincipalFilter extends AthenzPrincipalFilter {

    private static final Logger log = Logger.getLogger(UserAuthWithAthenzPrincipalFilter.class.getName());

    private final String userAuthenticationPassThruAttribute;

    @Inject
    public UserAuthWithAthenzPrincipalFilter(ZmsKeystore zmsKeystore, Executor executor, AthenzConfig config) {
        super(zmsKeystore, executor, config);
        this.userAuthenticationPassThruAttribute = config.userAuthenticationPassThruAttribute();
    }

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler responseHandler) {
        if (request.getMethod().equals("OPTIONS")) return; // Skip authentication on OPTIONS - required for Javascript CORS

        switch (getUserAuthenticationResult(request)) {
            case USER_COOKIE_MISSING:
            case USER_COOKIE_ALTERNATIVE_MISSING:
                super.filter(request, responseHandler); // Cookie-based authentication failed, delegate to Athenz
                break;
            case USER_COOKIE_OK:
                rewriteUserPrincipalToAthenz(request);
                return; // Authenticated using user cookie
            case USER_COOKIE_INVALID:
                sendUnauthorized(responseHandler, "Your user cookie is invalid (either expired, tampered or invalid ip)");
                break;
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

    /**
     * NOTE: The Bouncer user roles ({@link DiscFilterRequest#roles} are still intact as they are required
     * for {@link Authorizer#isMemberOfVespaBouncerGroup(HttpRequest)}.
     */
    private static void rewriteUserPrincipalToAthenz(DiscFilterRequest request) {
        Principal userPrincipal = request.getUserPrincipal();
        log.log(LogLevel.DEBUG, () -> "Original user principal: " + userPrincipal.toString());
        UserId userId = new UserId(userPrincipal.getName());
        AthenzPrincipal athenzPrincipal = AthenzUtils.createPrincipal(userId);
        request.setUserPrincipal(athenzPrincipal);
        request.setRemoteUser(athenzPrincipal.toYRN());
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
