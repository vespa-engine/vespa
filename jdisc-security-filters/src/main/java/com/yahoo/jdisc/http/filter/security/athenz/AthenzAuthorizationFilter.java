// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.google.inject.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilterConfig.CredentialsToVerify;
import com.yahoo.jdisc.http.filter.security.athenz.RequestResourceMapper.ResourceNameAndAction;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.tls.AthenzX509CertificateUtils;
import com.yahoo.vespa.athenz.zpe.AccessCheckResult;
import com.yahoo.vespa.athenz.zpe.DefaultZpe;
import com.yahoo.vespa.athenz.zpe.Zpe;

import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Collections.singletonList;

/**
 * An Athenz security filter that uses a configured action and resource name to control access.
 *
 * @author bjorncs
 */
public class AthenzAuthorizationFilter extends JsonSecurityRequestFilterBase {

    private final String headerName;
    private final Zpe zpe;
    private final RequestResourceMapper requestResourceMapper;
    private final CredentialsToVerify.Enum credentialsToVerify;

    @Inject
    public AthenzAuthorizationFilter(AthenzAuthorizationFilterConfig config, RequestResourceMapper resourceMapper) {
        this(config, resourceMapper, new DefaultZpe());
    }

    AthenzAuthorizationFilter(AthenzAuthorizationFilterConfig config,
                              RequestResourceMapper resourceMapper,
                              Zpe zpe) {
        this.headerName = config.roleTokenHeaderName();
        this.credentialsToVerify = config.credentialsToVerify();
        this.requestResourceMapper = resourceMapper;
        this.zpe = zpe;
    }

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        Optional<ResourceNameAndAction> resourceMapping =
                requestResourceMapper.getResourceNameAndAction(request.getMethod(), request.getRequestURI(), request.getQueryString());
        if (!resourceMapping.isPresent()) {
            return Optional.empty();
        }
        Optional<X509Certificate> roleCertificate = getRoleCertificate(request);
        Optional<ZToken> roleToken = getRoleToken(request, headerName);
        switch (credentialsToVerify) {
            case CERTIFICATE_ONLY: {
                if (!roleCertificate.isPresent()) {
                    return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, "Missing client certificate"));
                }
                return checkAccessAllowed(roleCertificate.get(), resourceMapping.get(), request);
            }
            case TOKEN_ONLY: {
                if (!roleToken.isPresent()) {
                    return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED,
                                                         String.format("Role token header '%s' is missing or does not have a value.", headerName)));
                }
                return checkAccessAllowed(roleToken.get(), resourceMapping.get(), request);
            }
            case ANY: {
                if (!roleCertificate.isPresent() && !roleToken.isPresent()) {
                    return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, "Both role token and role certificate is missing"));
                }
                if (roleCertificate.isPresent()) {
                    return checkAccessAllowed(roleCertificate.get(), resourceMapping.get(), request);
                } else {
                    return checkAccessAllowed(roleToken.get(), resourceMapping.get(), request);
                }
            }
            default: {
                throw new IllegalStateException("Unexpected mode: " + credentialsToVerify);
            }
        }
    }

    private static Optional<X509Certificate> getRoleCertificate(DiscFilterRequest request) {
        return Optional.of(request.getClientCertificateChain())
                .filter(chain -> !chain.isEmpty())
                .map(chain -> chain.get(0))
                .filter(AthenzX509CertificateUtils::isAthenzRoleCertificate);
    }

    private static Optional<ZToken> getRoleToken(DiscFilterRequest request, String headerName) {
        return Optional.ofNullable(request.getHeader(headerName))
                .filter(token -> !token.isEmpty())
                .map(ZToken::new);
    }

    private Optional<ErrorResponse> checkAccessAllowed(X509Certificate certificate,
                                                       ResourceNameAndAction resourceNameAndAction,
                                                       DiscFilterRequest request) {
        return checkAccessAllowed(
                certificate, resourceNameAndAction, request, zpe::checkAccessAllowed, AthenzAuthorizationFilter::createPrincipal);
    }

    private Optional<ErrorResponse> checkAccessAllowed(ZToken roleToken,
                                                       ResourceNameAndAction resourceNameAndAction,
                                                       DiscFilterRequest request) {
        return checkAccessAllowed(
                roleToken, resourceNameAndAction, request, zpe::checkAccessAllowed, AthenzAuthorizationFilter::createPrincipal);
    }

    private static <C> Optional<ErrorResponse> checkAccessAllowed(C credentials,
                                                                  ResourceNameAndAction resAndAction,
                                                                  DiscFilterRequest request,
                                                                  ZpeCheck<C> accessCheck,
                                                                  Function<C, AthenzPrincipal> principalFactory) {
        AccessCheckResult accessCheckResult = accessCheck.checkAccess(credentials, resAndAction.resourceName(), resAndAction.action());
        if (accessCheckResult == AccessCheckResult.ALLOW) {
            request.setUserPrincipal(principalFactory.apply(credentials));
            return Optional.empty();
        }
        return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access forbidden: " + accessCheckResult.getDescription()));
    }

    private static AthenzPrincipal createPrincipal(X509Certificate certificate) {
        AthenzIdentity identity = AthenzX509CertificateUtils.getIdentityFromRoleCertificate(certificate);
        AthenzRole role = AthenzX509CertificateUtils.getRolesFromRoleCertificate(certificate);
        return new AthenzPrincipal(identity, singletonList(role));
    }

    private static AthenzPrincipal createPrincipal(ZToken roleToken) {
        return new AthenzPrincipal(roleToken.getIdentity(), roleToken.getRoles());
    }

    @FunctionalInterface private interface ZpeCheck<C> {
        AccessCheckResult checkAccess(C credentials, AthenzResourceName resourceName, String action);
    }

}
