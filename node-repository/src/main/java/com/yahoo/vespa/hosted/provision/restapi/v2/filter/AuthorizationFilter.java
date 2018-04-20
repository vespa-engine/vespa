// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.NodeRepositoryConfig;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.restapi.v2.Authorizer;
import com.yahoo.vespa.hosted.provision.restapi.v2.ErrorResponse;

import java.net.URI;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Authorization filter for all paths in config server.
 *
 * @author mpolden
 * @author bjorncs
 */
public class AuthorizationFilter implements SecurityRequestFilter {

    private static final Logger log = Logger.getLogger(AuthorizationFilter.class.getName());

    private final BiPredicate<NodePrincipal, URI> authorizer;
    private final BiConsumer<ErrorResponse, ResponseHandler> rejectAction;
    private final HostAuthenticator hostAuthenticator;

    @Inject
    public AuthorizationFilter(Zone zone, NodeRepository nodeRepository, NodeRepositoryConfig nodeRepositoryConfig) {
        this(
                new Authorizer(
                        zone.system(),
                        nodeRepository,
                        Stream.concat(
                                Stream.of(HostName.getLocalhost()),
                                Stream.of(nodeRepositoryConfig.hostnameWhitelist().split(","))
                        ).filter(hostname -> !hostname.isEmpty()).collect(Collectors.toSet())),
                AuthorizationFilter::logAndReject,
                new HostAuthenticator(zone, nodeRepository)
        );
    }

    AuthorizationFilter(BiPredicate<NodePrincipal, URI> authorizer,
                        BiConsumer<ErrorResponse, ResponseHandler> rejectAction,
                        HostAuthenticator hostAuthenticator) {
        this.authorizer = authorizer;
        this.rejectAction = rejectAction;
        this.hostAuthenticator = hostAuthenticator;
    }

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        validateAccess(request)
                .ifPresent(errorResponse -> rejectAction.accept(errorResponse, handler));
    }

    private Optional<ErrorResponse> validateAccess(DiscFilterRequest request) {
        try {
            List<X509Certificate> clientCertificateChain = request.getClientCertificateChain();
            if (clientCertificateChain.isEmpty())
                return Optional.of(ErrorResponse.unauthorized(createErrorMessage(request, "Missing credentials")));
            NodePrincipal hostIdentity = hostAuthenticator.authenticate(clientCertificateChain);
            if (!authorizer.test(hostIdentity, request.getUri()))
                return Optional.of(ErrorResponse.forbidden(createErrorMessage(request, "Invalid credentials")));
            request.setUserPrincipal(hostIdentity);
            return Optional.empty();
        } catch (HostAuthenticator.AuthenticationException e) {
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
