// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.athenz.RequestResourceMapper.ResourceNameAndAction;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzRole;
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
import java.util.Objects;
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
    private static final String ACCEPTED_METRIC_NAME = ContainerMetrics.JDISC_HTTP_FILTER_ATHENZ_ACCEPTED_REQUESTS.baseName();
    private static final String REJECTED_METRIC_NAME = ContainerMetrics.JDISC_HTTP_FILTER_ATHENZ_REJECTED_REQUESTS.baseName();

    private static final Logger log = Logger.getLogger(AthenzAuthorizationFilter.class.getName());

    private final String roleTokenHeaderName;
    private final EnumSet<EnabledCredentials.Enum> enabledCredentials;
    private final Zpe zpe;
    private final RequestResourceMapper requestResourceMapper;
    private final Metric metric;
    private final Set<AthenzIdentity> allowedProxyIdentities;
    private final Optional<AthenzRole> readRole;
    private final Optional<AthenzRole> writeRole;

    @Inject
    public AthenzAuthorizationFilter(AthenzAuthorizationFilterConfig config, RequestResourceMapper resourceMapper, Metric metric) {
        this(config, resourceMapper, new DefaultZpe(), metric, null, null);
    }

    public AthenzAuthorizationFilter(AthenzAuthorizationFilterConfig config,
                                     RequestResourceMapper resourceMapper,
                                     Zpe zpe,
                                     Metric metric,
                                     AthenzRole readRole,
                                     AthenzRole writeRole) {
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
        this.readRole = Optional.ofNullable(readRole);
        this.writeRole = Optional.ofNullable(writeRole);
    }

    @Override
    public Optional<ErrorResponse> filter(DiscFilterRequest request) {
        try {
            Optional<ResourceNameAndAction> resourceMapping =
                    requestResourceMapper.getResourceNameAndAction(request);
            log.log(Level.FINE, () -> String.format("Resource mapping for '%s': %s", request, resourceMapping));
            if (resourceMapping.isEmpty()) {
                incrementAcceptedMetrics(request, false, Optional.empty());
                return Optional.empty();
            }
            Result result = checkAccessAllowed(request, resourceMapping.get());
            AuthorizationResult.Type resultType = result.zpeResult.type();
            setAttribute(request, RESULT_ATTRIBUTE, resultType.name());
            if (resultType == AuthorizationResult.Type.ALLOW) {
                populateRequestWithResult(request, result);
                incrementAcceptedMetrics(request, true, Optional.of(result));
                return Optional.empty();
            }
            log.log(Level.FINE, () -> String.format("Forbidden (403) for '%s': %s", request, resultType.name()));
            incrementRejectedMetrics(request, FORBIDDEN, resultType.name(), Optional.of(result));
            return Optional.of(new ErrorResponse(FORBIDDEN, "Access forbidden: " + resultType.getDescription()));
        } catch (IllegalArgumentException e) {
            log.log(Level.FINE, () -> String.format("Unauthorized (401) for '%s': %s", request, e.getMessage()));
            incrementRejectedMetrics(request, UNAUTHORIZED, "Unauthorized", Optional.empty());
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
            return checkAccessWithProxiedAccessToken(request, resourceAndAction, accessToken, identityCertificate);
        } else {
            var zpeResult = zpe.checkAccessAllowed(
                    accessToken, identityCertificate, resourceAndAction.resourceName(), resourceAndAction.action());
            return getResult(ACCESS_TOKEN, peerIdentity, zpeResult, request, resourceAndAction, mapToRequestPrivileges(accessToken.roles()));
        }
    }

    private Result getResult(EnabledCredentials.Enum credentialType, AthenzIdentity identity, AuthorizationResult zpeResult, DiscFilterRequest request, ResourceNameAndAction resourceAndAction, List<String> privileges) {
        return new Result(credentialType, identity, zpeResult, privileges, resourceAndAction.action());
    }

    private List<String> mapToRequestPrivileges(List<AthenzRole> roles) {
        return roles.stream()
                .map(this::rolePrivilege)
                .filter(Objects::nonNull)
                .toList();
    }

    private String rolePrivilege(AthenzRole role) {
        if (readRole.stream().anyMatch(role::equals)) return "read";
        if (writeRole.stream().anyMatch(role::equals)) return "write";
        return null;
    }

    private Result checkAccessWithProxiedAccessToken(DiscFilterRequest request, ResourceNameAndAction resourceAndAction, AthenzAccessToken accessToken, X509Certificate identityCertificate) {
        AthenzIdentity proxyIdentity = AthenzIdentities.from(identityCertificate);
        log.log(Level.FINE,
                () -> String.format("Checking proxied access token. Proxy identity: '%s'. Allowed identities: %s", proxyIdentity, allowedProxyIdentities));
        var zpeResult = zpe.checkAccessAllowed(accessToken, resourceAndAction.resourceName(), resourceAndAction.action());
        return getResult(ACCESS_TOKEN, AthenzIdentities.from(identityCertificate), zpeResult, request, resourceAndAction, mapToRequestPrivileges(accessToken.roles()));
    }

    private Result checkAccessWithRoleCertificate(DiscFilterRequest request, ResourceNameAndAction resourceAndAction) {
        X509Certificate roleCertificate = getClientCertificate(request).get();
        var zpeResult = zpe.checkAccessAllowed(roleCertificate, resourceAndAction.resourceName(), resourceAndAction.action());
        AthenzIdentity identity = AthenzX509CertificateUtils.getIdentityFromRoleCertificate(roleCertificate);
        AthenzX509CertificateUtils.getRolesFromRoleCertificate(roleCertificate).roleName();
        return getResult(ROLE_CERTIFICATE, identity, zpeResult, request, resourceAndAction, mapToRequestPrivileges(List.of(AthenzX509CertificateUtils.getRolesFromRoleCertificate(roleCertificate))));
    }

    private Result checkAccessWithRoleToken(DiscFilterRequest request, ResourceNameAndAction resourceAndAction) {
        ZToken roleToken = getRoleToken(request);
        var zpeResult = zpe.checkAccessAllowed(roleToken, resourceAndAction.resourceName(), resourceAndAction.action());
        return getResult(ROLE_TOKEN, roleToken.getIdentity(), zpeResult, request, resourceAndAction, mapToRequestPrivileges(roleToken.getRoles()));
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

    private void incrementAcceptedMetrics(DiscFilterRequest request, boolean authzRequired, Optional<Result> result) {
        String hostHeader = request.getHeader("Host");
        Metric.Context context = metric.createContext(Map.of(
                "endpoint", hostHeader != null ? hostHeader : "",
                "authz-required", Boolean.toString(authzRequired),
                "httpMethod", HttpRequest.Method.valueOf(request.getMethod()).name(),
                "requestPrivileges", result.map(r -> String.join(",", r.requestPrivileges)).orElse(""),
                "requestMapping", result.map(r -> r.action).orElse("")
        ));
        metric.add(ACCEPTED_METRIC_NAME, 1L, context);
    }

    private void incrementRejectedMetrics(DiscFilterRequest request, int statusCode, String zpeCode, Optional<Result> result) {
        String hostHeader = request.getHeader("Host");
        Metric.Context context = metric.createContext(Map.of(
                "endpoint", hostHeader != null ? hostHeader : "",
                "status-code", Integer.toString(statusCode),
                "zpe-status", zpeCode,
                "httpMethod", HttpRequest.Method.valueOf(request.getMethod()),
                "requestPrivileges", result.map(r -> String.join(",", r.requestPrivileges)).orElse(""),
                "action", result.map(r -> r.action).orElse("")
        ));
        metric.add(REJECTED_METRIC_NAME, 1L, context);
    }

    private static class Result {
        final EnabledCredentials.Enum credentialType;
        final AthenzIdentity identity;
        final AuthorizationResult zpeResult;
        final List<String> requestPrivileges;
        final String action;

        public Result(EnabledCredentials.Enum credentialType, AthenzIdentity identity, AuthorizationResult zpeResult, List<String> requestPrivileges, String action) {
            this.credentialType = credentialType;
            this.identity = identity;
            this.zpeResult = zpeResult;
            this.requestPrivileges = requestPrivileges;
            this.action = action;
        }
    }
}
