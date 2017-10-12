// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.server.jetty.ErrorResponseContentCreator;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.InvalidTokenException;
import com.yahoo.vespa.hosted.controller.athenz.NToken;
import com.yahoo.vespa.hosted.controller.athenz.ZmsKeystore;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Performs authentication by validating the principal token (NToken) header.
 *
 * @author bjorncs
 */
public class AthenzPrincipalFilter implements SecurityRequestFilter {

    private final ErrorResponseContentCreator responseCreator = new ErrorResponseContentCreator();
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
            sendUnauthorized(request, responseHandler, "NToken is missing");
            return;
        }
        try {
            AthenzPrincipal principal = validator.validate(new NToken(rawToken));
            request.setUserPrincipal(principal);
            request.setRemoteUser(principal.getName());
        } catch (InvalidTokenException e) {
            sendUnauthorized(request, responseHandler, e.getMessage());
        }
    }

    private void sendUnauthorized(DiscFilterRequest request, ResponseHandler responseHandler, String message) {
        try (FastContentWriter writer = ResponseDispatch.newInstance(Response.Status.UNAUTHORIZED)
                .connectFastWriter(responseHandler)) {
            writer.write(
                    responseCreator.createErrorContent(
                            request.getRequestURI(), Response.Status.UNAUTHORIZED, Optional.of(message)));
        }
    }

}
