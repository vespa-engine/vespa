// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
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
    public AuthorizationFilter(Zone zone, NodeRepository nodeRepository) {
        this(new Authorizer(zone.system(), nodeRepository), rejectActionIn(zone.system()));
    }

    AuthorizationFilter(BiPredicate<Principal, URI> authorizer,
                        BiConsumer<ErrorResponse, ResponseHandler> rejectAction) {
        this.authorizer = authorizer;
        this.rejectAction = rejectAction;
    }

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        Optional<X509Certificate> cert = request.getClientCertificateChain().stream().findFirst();
        if (cert.isPresent()) {
            if (!authorizer.test(() -> commonName(cert.get()), request.getUri())) {
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

    private static BiConsumer<ErrorResponse, ResponseHandler> rejectActionIn(SystemName system) {
        if (system == SystemName.cd) {
            return AuthorizationFilter::logAndReject;
        }
        return AuthorizationFilter::log;
    }

    private static void log(ErrorResponse response, @SuppressWarnings("unused") ResponseHandler handler) {
        log.warning("Would reject request: " + response.getStatus() + " - " + response.message());
    }

    private static void logAndReject(ErrorResponse response, ResponseHandler handler) {
        log.warning(response.message());
        FilterUtils.write(response, handler);
    }

    /** Read common name (CN) from certificate */
    private static String commonName(X509Certificate certificate) {
        return X509CertificateUtils.getCommonNames(certificate).get(0);
    }

}
