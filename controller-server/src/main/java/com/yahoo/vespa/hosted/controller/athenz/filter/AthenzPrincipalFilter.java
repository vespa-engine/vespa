// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsKeystore;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.yahoo.vespa.hosted.controller.athenz.filter.SecurityFilterUtils.sendErrorResponse;

/**
 * Authenticates Athenz principal, either through:
 *  1. TLS client authentication (based on Athenz x509 identity certficiate).
 *  2. The principal token (NToken) header.
 * The TLS authentication is based on the following assumptions:
 *  - The underlying connector is configured with 'clientAuth' set to either WANT_AUTH or NEED_AUTH.
 *  - The trust store is configured with the Athenz CA certificates only.
 *
 * @author bjorncs
 */
// TODO bjorncs: Move this class to vespa-athenz bundle
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
        try {
            Optional<AthenzPrincipal> certificatePrincipal = getClientCertificate(request)
                    .map(AthenzIdentities::from)
                    .map(AthenzPrincipal::new);
            Optional<AthenzPrincipal> nTokenPrincipal = getPrincipalToken(request, principalTokenHeader)
                    .map(validator::validate);

            if (!certificatePrincipal.isPresent() && !nTokenPrincipal.isPresent()) {
                String errorMessage = "Unable to authenticate Athenz identity. " +
                        "Either client certificate or principal token is required.";
                sendErrorResponse(responseHandler, Response.Status.UNAUTHORIZED, errorMessage);
                return;
            }
            if (certificatePrincipal.isPresent() && nTokenPrincipal.isPresent()
                    && !certificatePrincipal.get().getIdentity().equals(nTokenPrincipal.get().getIdentity())) {
                String errorMessage = String.format(
                        "Identity in principal token does not match x509 CN: token-identity=%s, cert-identity=%s",
                        nTokenPrincipal.get().getIdentity().getFullName(),
                        certificatePrincipal.get().getIdentity().getFullName());
                sendErrorResponse(responseHandler, Response.Status.UNAUTHORIZED, errorMessage);
                return;
            }
            AthenzPrincipal principal = nTokenPrincipal.orElseGet(certificatePrincipal::get);
            request.setUserPrincipal(principal);
            request.setRemoteUser(principal.getName());
        } catch (Exception e) {
            sendErrorResponse(responseHandler,Response.Status.UNAUTHORIZED, e.getMessage());
        }
    }

    private static Optional<X509Certificate> getClientCertificate(DiscFilterRequest request) {
        return Optional.ofNullable((X509Certificate[]) request.getAttribute("jdisc.request.X509Certificate"))
                .map(chain -> chain[0]);
    }

    private static Optional<NToken> getPrincipalToken(DiscFilterRequest request, String principalTokenHeaderName) {
        return Optional.ofNullable(request.getHeader(principalTokenHeaderName))
                .filter(token -> !token.isEmpty())
                .map(NToken::new);
    }

}
