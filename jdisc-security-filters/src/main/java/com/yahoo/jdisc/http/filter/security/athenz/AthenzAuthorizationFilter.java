// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.athenz.RequestResourceMapper.ResourceNameAndAction;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.tls.AthenzX509CertificateUtils;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.vespa.athenz.zpe.AuthorizationResult;
import com.yahoo.vespa.athenz.zpe.DefaultZpe;
import com.yahoo.vespa.athenz.zpe.Zpe;

import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.jdisc.Response.Status.FORBIDDEN;
import static com.yahoo.jdisc.Response.Status.UNAUTHORIZED;
import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilterConfig.EnabledCredentials;
import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilterConfig.EnabledCredentials.ACCESS_TOKEN;
import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilterConfig.EnabledCredentials.ROLE_CERTIFICATE;
import static com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilterConfig.EnabledCredentials.ROLE_TOKEN;

/**
 * An Athenz security filter that uses a configured action and resource name to control access.
 *
 * @author bjorncs
 */
public class AthenzAuthorizationFilter extends JsonSecurityRequestFilterBase {

    private static final String ATTRIBUTE_PREFIX = "jdisc-security-filters.athenz-authorization-filter";
    public static final String RESULT_ATTRIBUTE = ATTRIBUTE_PREFIX + ".result";
    public static final String MATCHED_ROLE_ATTRIBUTE = ATTRIBUTE_PREFIX + ".matched-role";
    public static final String IDENTITY_NAME_ATTRIBUTE = ATTRIBUTE_PREFIX + ".identity-name";
    public static final String MATCHED_CREDENTIAL_TYPE_ATTRIBUTE = ATTRIBUTE_PREFIX + ".credentials-type";
    private static final String ACCEPTED_METRIC_NAME = "jdisc.http.filter.athenz.accepted_requests";
    private static final String REJECTED_METRIC_NAME = "jdisc.http.filter.athenz.rejected_requests";

    private static final Logger log = Logger.getLogger(AthenzAuthorizationFilter.class.getName());

    private final String roleTokenHeaderName;
    private final EnumSet<EnabledCredentials.Enum> enabledCredentials;
    private final Zpe zpe;
    private final RequestResourceMapper requestResourceMapper;
    private final Metric metric;
    private final Set<AthenzIdentity> allowedProxyIdentities;

    @Inject
    public AthenzAuthorizationFilter(AthenzAuthorizationFilterConfig config, RequestResourceMapper resourceMapper, Metric metric) {
        this(config, resourceMapper, new DefaultZpe(), metric);
    }

    public AthenzAuthorizationFilter(AthenzAuthorizationFilterConfig config,
                                     RequestResourceMapper resourceMapper,
                                     Zpe zpe,
                                     Metric metric) {
        this.roleTokenHeaderName = config.roleTokenHeaderName();
        List<EnabledCredentials.Enum> enabledCredentials = config.enabledCredentials();
        this.enabledCredentials = enabledCredentials.isEmpty()
                ? EnumSet.allOf(EnabledCredentials.Enum.class)
                : EnumSet.copyOf(enabledCredentials);
        this.requestResourceMapper = resourceMapper;
        this.zpe = zpe;
        this.metric = metric;
        this.allowedProxyIdentities = config.allowedProxyIdentities().stream()
                .map(AthenzIdentities::from)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ErrorResponse> filter(DiscFilterRequest request) {
        try {
            Optional<ResourceNameAndAction> resourceMapping =
                    requestResourceMapper.getResourceNameAndAction(request.getMethod(), request.getRequestURI(), request.getQueryString());
            log.log(Level.FINE, () -> String.format("Resource mapping for '%s': %s", request, resourceMapping));
            if (resourceMapping.isEmpty()) {
                incrementAcceptedMetrics(request, false);
                return Optional.empty();
            }
            Result result = checkAccessAllowed(request, resourceMapping.get());
            AuthorizationResult.Type resultType = result.zpeResult.type();
            setAttribute(request, RESULT_ATTRIBUTE, resultType.name());
            if (resultType == AuthorizationResult.Type.ALLOW) {
                populateRequestWithResult(request, result);
                incrementAcceptedMetrics(request, true);
                return Optional.empty();
            }
            log.log(Level.FINE, () -> String.format("Forbidden (403) for '%s': %s", request, resultType.name()));
            incrementRejectedMetrics(request, FORBIDDEN, resultType.name());
            return Optional.of(new ErrorResponse(FORBIDDEN, "Access forbidden: " + resultType.getDescription()));
        } catch (IllegalArgumentException e) {
            log.log(Level.FINE, () -> String.format("Unauthorized (401) for '%s': %s", request, e.getMessage()));
            incrementRejectedMetrics(request, UNAUTHORIZED, "Unauthorized");
            return Optional.of(new ErrorResponse(UNAUTHORIZED, e.getMessage()));
        }
    }

    private Result checkAccessAllowed(DiscFilterRequest request, ResourceNameAndAction resourceAndAction) {
        // Note: the ordering of the if-constructs determines the precedence of the credential types
        if (enabledCredentials.contains(ACCESS_TOKEN)
                && isAccessTokenPresent(request)
                && isIdentityCertificatePresent(request)) {
            return checkAccessWithAccessToken(request, resourceAndAction);
        } else if (enabledCredentials.contains(ROLE_CERTIFICATE)
                && isRoleCertificatePresent(request)) {
            return checkAccessWithRoleCertificate(request, resourceAndAction);
        } else if (enabledCredentials.contains(ROLE_TOKEN)
                && isRoleTokenPresent(request)) {
            return checkAccessWithRoleToken(request, resourceAndAction);
        } else {
            throw new IllegalArgumentException(
                    "Not authorized - request did not contain any of the allowed credentials: " + toPrettyString(enabledCredentials));
        }
    }

    private Result checkAccessWithAccessToken(DiscFilterRequest request, ResourceNameAndAction resourceAndAction) {
        AthenzAccessToken accessToken = getAccessToken(request);
        X509Certificate identityCertificate = getClientCertificate(request).get();
        AthenzIdentity peerIdentity = AthenzIdentities.from(identityCertificate);
        if (allowedProxyIdentities.contains(peerIdentity)) {
            return checkAccessWithProxiedAccessToken(resourceAndAction, accessToken, identityCertificate);
        } else {
            var zpeResult = zpe.checkAccessAllowed(
                    accessToken, identityCertificate, resourceAndAction.resourceName(), resourceAndAction.action());
            return new Result(ACCESS_TOKEN, peerIdentity, zpeResult);
        }
    }

    private Result checkAccessWithProxiedAccessToken(ResourceNameAndAction resourceAndAction, AthenzAccessToken accessToken, X509Certificate identityCertificate) {
        AthenzIdentity proxyIdentity = AthenzIdentities.from(identityCertificate);
        log.log(Level.FINE,
                () -> String.format("Checking proxied access token. Proxy identity: '%s'. Allowed identities: %s", proxyIdentity, allowedProxyIdentities));
        var zpeResult = zpe.checkAccessAllowed(accessToken, resourceAndAction.resourceName(), resourceAndAction.action());
        return new Result(ACCESS_TOKEN, AthenzIdentities.from(identityCertificate), zpeResult);
    }

    private Result checkAccessWithRoleCertificate(DiscFilterRequest request, ResourceNameAndAction resourceAndAction) {
        X509Certificate roleCertificate = getClientCertificate(request).get();
        var zpeResult = zpe.checkAccessAllowed(roleCertificate, resourceAndAction.resourceName(), resourceAndAction.action());
        AthenzIdentity identity = AthenzX509CertificateUtils.getIdentityFromRoleCertificate(roleCertificate);
        return new Result(ROLE_CERTIFICATE, identity, zpeResult);
    }

    private Result checkAccessWithRoleToken(DiscFilterRequest request, ResourceNameAndAction resourceAndAction) {
        ZToken roleToken = getRoleToken(request);
        var zpeResult = zpe.checkAccessAllowed(roleToken, resourceAndAction.resourceName(), resourceAndAction.action());
        return new Result(ROLE_TOKEN, roleToken.getIdentity(), zpeResult);
    }

    private static boolean isAccessTokenPresent(DiscFilterRequest request) {
        return request.getHeader(AthenzAccessToken.HTTP_HEADER_NAME) != null;
    }

    // Check that client certificate looks like a role certificate
    private static boolean isRoleCertificatePresent(DiscFilterRequest request) {
        return getClientCertificate(request)
                .filter(cert -> {
                    try {
                        AthenzX509CertificateUtils.getRolesFromRoleCertificate(cert);
                        return true;
                    } catch (Exception e) {
                        log.log(Level.FINE, e, () -> "Not a role certificate: " + e.getMessage());
                        return false;
                    }
                })
                .isPresent();
    }

    // Check that client certificate looks like an identity certificate
    private static boolean isIdentityCertificatePresent(DiscFilterRequest request) {
        return getClientCertificate(request)
                .filter(cert -> {
                    try {
                        AthenzIdentities.from(cert);
                        return true;
                    } catch (Exception e) {
                        log.log(Level.FINE, e, () -> "Not an identity certificate: " + e.getMessage());
                        return false;
                    }
                })
                .isPresent();
    }

    private boolean isRoleTokenPresent(DiscFilterRequest request) {
        return request.getHeader(roleTokenHeaderName) != null;
    }

    private static AthenzAccessToken getAccessToken(DiscFilterRequest request) {
        return new AthenzAccessToken(request.getHeader(AthenzAccessToken.HTTP_HEADER_NAME));
    }

    private static Optional<X509Certificate> getClientCertificate(DiscFilterRequest request) {
        List<X509Certificate> certificates = request.getClientCertificateChain();
        if (certificates.isEmpty()) return Optional.empty();
        return Optional.of(certificates.get(0));
    }

    private ZToken getRoleToken(DiscFilterRequest request) {
        return new ZToken(request.getHeader(roleTokenHeaderName));
    }

    private static String toPrettyString(EnumSet<EnabledCredentials.Enum> enabledCredentialSet) {
        return enabledCredentialSet.stream()
                .map(AthenzAuthorizationFilter::toPrettyString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String toPrettyString(EnabledCredentials.Enum enabledCredential) {
        switch (enabledCredential) {
            case ACCESS_TOKEN:
                return "Athenz access token with X.509 identity certificate";
            case ROLE_TOKEN:
                return "Athenz role token (ZToken)";
            case ROLE_CERTIFICATE:
                return "Athenz X.509 role certificate";
            default:
                throw new IllegalArgumentException("Unknown credential type: " + enabledCredential);
        }
    }

    private static void populateRequestWithResult(DiscFilterRequest request, Result result) {
        request.setUserPrincipal(
                new AthenzPrincipal(result.identity, result.zpeResult.matchedRole().map(List::of).orElse(List.of())));
        result.zpeResult.matchedRole().ifPresent(role -> {
            request.setUserRoles(new String[]{role.roleName()});
            setAttribute(request, MATCHED_ROLE_ATTRIBUTE, role.roleName());
        });
        setAttribute(request, IDENTITY_NAME_ATTRIBUTE, result.identity.getFullName());
        setAttribute(request, MATCHED_CREDENTIAL_TYPE_ATTRIBUTE, result.credentialType.name());
    }

    private static void setAttribute(DiscFilterRequest request, String name, String value) {
        log.log(Level.FINE, () -> String.format("Setting attribute on '%s': '%s' = '%s'", request, name, value));
        request.setAttribute(name, value);
    }

    private void incrementAcceptedMetrics(DiscFilterRequest request, boolean authzRequired) {
        String hostHeader = request.getHeader("Host");
        Metric.Context context = metric.createContext(Map.of(
                "endpoint", hostHeader != null ? hostHeader : "",
                "authz-required", Boolean.toString(authzRequired)));
        metric.add(ACCEPTED_METRIC_NAME, 1L, context);
    }

    private void incrementRejectedMetrics(DiscFilterRequest request, int statusCode, String zpeCode) {
        String hostHeader = request.getHeader("Host");
        Metric.Context context = metric.createContext(Map.of(
                "endpoint", hostHeader != null ? hostHeader : "",
                "status-code", Integer.toString(statusCode),
                "zpe-status", zpeCode));
        metric.add(REJECTED_METRIC_NAME, 1L, context);
    }

    private static class Result {
        final EnabledCredentials.Enum credentialType;
        final AthenzIdentity identity;
        final AuthorizationResult zpeResult;

        Result(EnabledCredentials.Enum credentialType, AthenzIdentity identity, AuthorizationResult zpeResult) {
            this.credentialType = credentialType;
            this.identity = identity;
            this.zpeResult = zpeResult;
        }
    }
}
