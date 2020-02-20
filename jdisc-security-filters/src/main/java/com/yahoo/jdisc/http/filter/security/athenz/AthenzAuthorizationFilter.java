// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.google.inject.Inject;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.athenz.RequestResourceMapper.ResourceNameAndAction;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.log.LogLevel;
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
import java.util.Optional;
import java.util.logging.Logger;

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

    private static final Logger log = Logger.getLogger(AthenzAuthorizationFilter.class.getName());

    private final String roleTokenHeaderName;
    private final EnumSet<EnabledCredentials.Enum> enabledCredentials;
    private final Zpe zpe;
    private final RequestResourceMapper requestResourceMapper;

    @Inject
    public AthenzAuthorizationFilter(AthenzAuthorizationFilterConfig config, RequestResourceMapper resourceMapper) {
        this(config, resourceMapper, new DefaultZpe());
    }

    public AthenzAuthorizationFilter(AthenzAuthorizationFilterConfig config,
                                     RequestResourceMapper resourceMapper,
                                     Zpe zpe) {
        this.roleTokenHeaderName = config.roleTokenHeaderName();
        List<EnabledCredentials.Enum> enabledCredentials = config.enabledCredentials();
        this.enabledCredentials = enabledCredentials.isEmpty()
                ? EnumSet.allOf(EnabledCredentials.Enum.class)
                : EnumSet.copyOf(enabledCredentials);
        this.requestResourceMapper = resourceMapper;
        this.zpe = zpe;
    }

    @Override
    public Optional<ErrorResponse> filter(DiscFilterRequest request) {
        try {
            Optional<ResourceNameAndAction> resourceMapping =
                    requestResourceMapper.getResourceNameAndAction(request.getMethod(), request.getRequestURI(), request.getQueryString());
            log.log(LogLevel.DEBUG, () -> String.format("Resource mapping for '%s': %s", request, resourceMapping));
            if (resourceMapping.isEmpty()) {
                return Optional.empty();
            }
            Result result = checkAccessAllowed(request, resourceMapping.get());
            AuthorizationResult.Type resultType = result.zpeResult.type();
            setAttribute(request, RESULT_ATTRIBUTE, resultType.name());
            if (resultType == AuthorizationResult.Type.ALLOW) {
                populateRequestWithResult(request, result);
                return Optional.empty();
            }
            log.log(LogLevel.DEBUG, () -> String.format("Forbidden (403) for '%s': %s", request, resultType.name()));
            return Optional.of(new ErrorResponse(FORBIDDEN, "Access forbidden: " + resultType.getDescription()));
        } catch (IllegalArgumentException e) {
            log.log(LogLevel.DEBUG, () -> String.format("Unauthorized (401) for '%s': %s", request, e.getMessage()));
            return Optional.of(new ErrorResponse(UNAUTHORIZED, e.getMessage()));
        }
    }

    private Result checkAccessAllowed(DiscFilterRequest request, ResourceNameAndAction resourceAndAction) {
        // Note: the ordering of the if-constructs determines the precedence of the credential types
        if (enabledCredentials.contains(ACCESS_TOKEN)
                && isAccessTokenPresent(request)
                && isClientCertificatePresent(request)) {
            return checkAccessWithAccessToken(request, resourceAndAction);
        } else if (enabledCredentials.contains(ROLE_CERTIFICATE)
                && isClientCertificatePresent(request)) {
            return checkAccessWithRoleCertificate(request, resourceAndAction);
        } else if (enabledCredentials.contains(ROLE_TOKEN)
                && isRoleTokenPresent(request)) {
            return checkAccessWithRoleToken(request, resourceAndAction);
        } else {
            throw new IllegalArgumentException(
                    "Not authorized - request did not contain any of the allowed credentials: " + enabledCredentials);
        }
    }

    private Result checkAccessWithAccessToken(DiscFilterRequest request, ResourceNameAndAction resourceAndAction) {
        AthenzAccessToken accessToken = getAccessToken(request);
        X509Certificate identityCertificate = getClientCertificate(request);
        var zpeResult = zpe.checkAccessAllowed(
                accessToken, identityCertificate, resourceAndAction.resourceName(), resourceAndAction.action());
        return new Result(ACCESS_TOKEN, AthenzIdentities.from(identityCertificate), zpeResult);
    }

    private Result checkAccessWithRoleCertificate(DiscFilterRequest request, ResourceNameAndAction resourceAndAction) {
        X509Certificate roleCertificate = getClientCertificate(request);
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

    private static boolean isClientCertificatePresent(DiscFilterRequest request) {
        return !request.getClientCertificateChain().isEmpty();
    }

    private boolean isRoleTokenPresent(DiscFilterRequest request) {
        return request.getHeader(roleTokenHeaderName) != null;
    }

    private static AthenzAccessToken getAccessToken(DiscFilterRequest request) {
        return new AthenzAccessToken(request.getHeader(AthenzAccessToken.HTTP_HEADER_NAME));
    }

    private static X509Certificate getClientCertificate(DiscFilterRequest request) {
        return request.getClientCertificateChain().get(0);
    }

    private ZToken getRoleToken(DiscFilterRequest request) {
        return new ZToken(request.getHeader(roleTokenHeaderName));
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
        log.log(LogLevel.DEBUG, () -> String.format("Setting attribute on '%s': '%s' = '%s'", request, name, value));
        request.setAttribute(name, value);
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
