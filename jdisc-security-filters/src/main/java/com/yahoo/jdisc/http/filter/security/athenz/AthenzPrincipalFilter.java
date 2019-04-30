// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.google.inject.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.vespa.athenz.utils.ntoken.NTokenValidator;

import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;


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
public class AthenzPrincipalFilter extends JsonSecurityRequestFilterBase {

    private static final String RESULT_ATTRIBUTE_PREFIX = "jdisc-security-filters.athenz-principal-filter.result";
    public static final String RESULT_ERROR_CODE_ATTRIBUTE = RESULT_ATTRIBUTE_PREFIX + ".error.code";
    public static final String RESULT_ERROR_MESSAGE_ATTRIBUTE = RESULT_ATTRIBUTE_PREFIX + ".error.message";
    public static final String RESULT_PRINCIPAL = RESULT_ATTRIBUTE_PREFIX + ".principal";

    private final NTokenValidator validator;
    private final String principalTokenHeader;
    private final boolean passthroughMode;

    @Inject
    public AthenzPrincipalFilter(AthenzPrincipalFilterConfig athenzPrincipalFilterConfig) {
        this(new NTokenValidator(Paths.get(athenzPrincipalFilterConfig.athenzConfFile())),
             athenzPrincipalFilterConfig.principalHeaderName(),
             athenzPrincipalFilterConfig.passthroughMode());
    }

    AthenzPrincipalFilter(NTokenValidator validator,
                          String principalTokenHeader,
                          boolean passthroughMode) {
        this.validator = validator;
        this.principalTokenHeader = principalTokenHeader;
        this.passthroughMode = passthroughMode;
    }

    @Override
    public Optional<ErrorResponse> filter(DiscFilterRequest request) {
        try {
            Optional<AthenzPrincipal> certificatePrincipal = getClientCertificate(request)
                    .map(AthenzIdentities::from)
                    .map(AthenzPrincipal::new);
            Optional<AthenzPrincipal> nTokenPrincipal = getPrincipalToken(request, principalTokenHeader)
                    .map(validator::validate);

            if (!certificatePrincipal.isPresent() && !nTokenPrincipal.isPresent()) {
                String errorMessage = "Unable to authenticate Athenz identity. " +
                                      "Either client certificate or principal token is required.";
                return createResponse(request, Response.Status.UNAUTHORIZED, errorMessage);
            }
            if (certificatePrincipal.isPresent() && nTokenPrincipal.isPresent()
                    && !certificatePrincipal.get().getIdentity().equals(nTokenPrincipal.get().getIdentity())) {
                String errorMessage = String.format(
                        "Identity in principal token does not match x509 CN: token-identity=%s, cert-identity=%s",
                        nTokenPrincipal.get().getIdentity().getFullName(),
                        certificatePrincipal.get().getIdentity().getFullName());
                return createResponse(request, Response.Status.UNAUTHORIZED, errorMessage);
            }
            AthenzPrincipal principal = nTokenPrincipal.orElseGet(certificatePrincipal::get);
            request.setUserPrincipal(principal);
            request.setRemoteUser(principal.getName());
            request.setAttribute(RESULT_PRINCIPAL, principal);
            return Optional.empty();
        } catch (Exception e) {
            return createResponse(request, Response.Status.UNAUTHORIZED, e.getMessage());
        }
    }

    private static Optional<X509Certificate> getClientCertificate(DiscFilterRequest request) {
        List<X509Certificate> chain = request.getClientCertificateChain();
        if (chain.isEmpty()) return Optional.empty();
        return Optional.of(chain.get(0));
    }

    private static Optional<NToken> getPrincipalToken(DiscFilterRequest request, String principalTokenHeaderName) {
        return Optional.ofNullable(request.getHeader(principalTokenHeaderName))
                .filter(token -> !token.isEmpty())
                .map(NToken::new);
    }

    private Optional<ErrorResponse> createResponse(DiscFilterRequest request, int statusCode, String message) {
        request.setAttribute(RESULT_ERROR_CODE_ATTRIBUTE, statusCode);
        request.setAttribute(RESULT_ERROR_MESSAGE_ATTRIBUTE, message);
        if (passthroughMode) {
            return Optional.empty();
        } else {
            return Optional.of(new ErrorResponse(statusCode, message));
        }
    }

}
