// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.FilterConfig;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.restapi.v2.ErrorResponse;
import com.yahoo.yolean.chain.After;

import java.net.URI;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

/**
 * Authorization filter for all paths in config server. It assumes that {@link NodeIdentifierFilter} is part of filter chain.
 *
 * @author mpolden
 * @author bjorncs
 */
@After("NodeIdentifierFilter")
public class AuthorizationFilter implements SecurityRequestFilter {

    private static final Logger log = Logger.getLogger(AuthorizationFilter.class.getName());

    private final BiPredicate<NodePrincipal, URI> authorizer;
    private final BiConsumer<ErrorResponse, ResponseHandler> rejectAction;

    @Inject
    public AuthorizationFilter(NodeRepository nodeRepository, FilterConfig filterConfig) {
        this.authorizer = new Authorizer(nodeRepository,
                AthenzIdentities.from(filterConfig.getInitParameter("controller.identity")),
                AthenzIdentities.from(filterConfig.getInitParameter("configserver.identity")),
                AthenzIdentities.from(filterConfig.getInitParameter("proxy.identity")),
                AthenzIdentities.from(filterConfig.getInitParameter("tenant-host.identity")));
        this.rejectAction = AuthorizationFilter::logAndReject;
    }

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        validateAccess(request)
                .ifPresent(errorResponse -> rejectAction.accept(errorResponse, handler));
    }

    private Optional<ErrorResponse> validateAccess(DiscFilterRequest request) {
        try {
            NodePrincipal hostIdentity = (NodePrincipal) request.getUserPrincipal();
            if (hostIdentity == null)
                return Optional.of(ErrorResponse.internalServerError(createErrorMessage(request, "Principal is missing. NodeIdentifierFilter has not been applied.")));
            if (!authorizer.test(hostIdentity, request.getUri()))
                return Optional.of(ErrorResponse.forbidden(createErrorMessage(request, "Invalid credentials: " + hostIdentity.toString())));
            request.setUserPrincipal(hostIdentity);
            return Optional.empty();
        } catch (NodeIdentifier.NodeIdentifierException e) {
            return Optional.of(ErrorResponse.forbidden(createErrorMessage(request, "Invalid credentials: " + e.getMessage())));
        }
    }

    private static String createErrorMessage(DiscFilterRequest request, String message) {
        return String.format("%s %s denied for %s: %s",
                             request.getMethod(),
                             request.getUri().getPath(),
                             request.getRemoteAddr(),
                             message);
    }

    private static void logAndReject(ErrorResponse response, ResponseHandler handler) {
        log.warning(response.message());
        FilterUtils.write(response, handler);
    }

}
