// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.vespa.hosted.controller.athenz.ZmsKeystore;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;
import com.yahoo.yolean.chain.Provides;

import java.util.concurrent.Executor;

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

    private final String userAuthenticationPassThruAttribute;

    @Inject
    public UserAuthWithAthenzPrincipalFilter(ZmsKeystore zmsKeystore, Executor executor, AthenzConfig config) {
        super(zmsKeystore, executor, config);
        this.userAuthenticationPassThruAttribute = config.userAuthenticationPassThruAttribute();
    }

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler responseHandler) {
        if (request.getMethod().equals("OPTIONS")) return; // Skip authentication on OPTIONS - required for Javascript CORS

        switch (fromHttpRequest(request)) {
            case USER_COOKIE_MISSING:
            case USER_COOKIE_ALTERNATIVE_MISSING:
                super.filter(request, responseHandler); // Cookie-based authentication failed, delegate to Athenz
                break;
            case USER_COOKIE_OK:
                return; // Authenticated using user cookie
            case USER_COOKIE_INVALID:
                sendUnauthorized(request, responseHandler, "Your user cookie is invalid (either expired or tampered)");
                break;
        }
    }

    private UserAuthenticationResult fromHttpRequest(DiscFilterRequest request) {
        if (!request.containsAttribute(userAuthenticationPassThruAttribute)) {
            throw new IllegalStateException("User authentication filter passthru attribute missing");
        }
        Integer statusCode = (Integer) request.getAttribute(userAuthenticationPassThruAttribute);
        for (UserAuthenticationResult result : UserAuthenticationResult.values()) {
            if (result.statusCode == statusCode) {
                return result;
            }
        }
        throw new IllegalStateException("Invalid status code: " + statusCode);
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
