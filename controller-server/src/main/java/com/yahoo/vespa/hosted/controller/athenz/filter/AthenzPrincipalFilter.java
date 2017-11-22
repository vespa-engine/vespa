// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.InvalidTokenException;
import com.yahoo.vespa.hosted.controller.athenz.NToken;
import com.yahoo.vespa.hosted.controller.athenz.ZmsKeystore;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.util.concurrent.Executor;

import static com.yahoo.vespa.hosted.controller.athenz.filter.SecurityFilterUtils.sendErrorResponse;

/**
 * Performs authentication by validating the principal token (NToken) header.
 *
 * @author bjorncs
 */
// TODO bjorncs: Move this class into separate container-security bundle
public class AthenzPrincipalFilter implements SecurityRequestFilter {

    private final NTokenValidator validator;
    private final String principalTokenHeader;

    /**
     * @param executor to preload the ZMS public keys with
     */
    @Inject
    public AthenzPrincipalFilter(ZmsKeystore zmsKeystore, Executor executor, AthenzConfig config) {
        this(new NTokenValidator(zmsKeystore), executor, config.principalHeaderName());
    }

    AthenzPrincipalFilter(NTokenValidator validator, Executor executor, String principalTokenHeader) {
        this.validator = validator;
        this.principalTokenHeader = principalTokenHeader;
        executor.execute(validator::preloadPublicKeys);
    }

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler responseHandler) {
        String rawToken = request.getHeader(principalTokenHeader);
        if (rawToken == null || rawToken.isEmpty()) {
            sendErrorResponse(responseHandler, Response.Status.UNAUTHORIZED, "NToken is missing");
            return;
        }
        try {
            AthenzPrincipal principal = validator.validate(new NToken(rawToken));
            request.setUserPrincipal(principal);
            request.setRemoteUser(principal.getName());
        } catch (InvalidTokenException e) {
            sendErrorResponse(responseHandler,Response.Status.UNAUTHORIZED, e.getMessage());
        }
    }

}
