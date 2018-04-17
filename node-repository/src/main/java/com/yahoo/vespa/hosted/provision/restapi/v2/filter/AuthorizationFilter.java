// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.NodeRepositoryConfig;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.net.HostName;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.restapi.v2.Authorizer;
import com.yahoo.vespa.hosted.provision.restapi.v2.ErrorResponse;

import java.net.URI;
import java.security.Principal;
import java.security.cert.X509Certificate;
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
 */
public class AuthorizationFilter implements SecurityRequestFilter {

    private static final Logger log = Logger.getLogger(AuthorizationFilter.class.getName());

    private final BiPredicate<Principal, URI> authorizer;
    private final BiConsumer<ErrorResponse, ResponseHandler> rejectAction;

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
                AuthorizationFilter::logAndReject
        );
    }

    AuthorizationFilter(BiPredicate<Principal, URI> authorizer,
                        BiConsumer<ErrorResponse, ResponseHandler> rejectAction) {
        this.authorizer = authorizer;
        this.rejectAction = rejectAction;
    }

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        Optional<String> commonName = request.getClientCertificateChain().stream()
                .findFirst()
                .flatMap(AuthorizationFilter::commonName);
        if (commonName.isPresent()) {
            if (!authorizer.test(commonName::get, request.getUri())) {
                rejectAction.accept(ErrorResponse.forbidden(
                        String.format("%s %s denied for %s: Invalid credentials", request.getMethod(),
                                      request.getUri().getPath(), request.getRemoteAddr())), handler
                );
            }
        } else {
            rejectAction.accept(ErrorResponse.unauthorized(
                    String.format("%s %s denied for %s: Missing credentials", request.getMethod(),
                                  request.getUri().getPath(), request.getRemoteAddr())), handler
            );
        }
    }

    private static void logAndReject(ErrorResponse response, ResponseHandler handler) {
        log.warning(response.message());
        FilterUtils.write(response, handler);
    }

    /** Read common name (CN) from certificate */
    private static Optional<String> commonName(X509Certificate certificate) {
        return X509CertificateUtils.getCommonNames(certificate).stream().findFirst();
    }

}
