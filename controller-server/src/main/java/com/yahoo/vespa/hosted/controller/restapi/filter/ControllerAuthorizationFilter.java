// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.cors.CorsFilterConfig;
import com.yahoo.jdisc.http.filter.security.cors.CorsRequestFilterBase;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsException;
import com.yahoo.vespa.hosted.controller.restapi.Path;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.yolean.chain.After;
import com.yahoo.yolean.chain.Provides;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.WebApplicationException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.HEAD;
import static com.yahoo.jdisc.http.HttpRequest.Method.OPTIONS;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;
import static com.yahoo.vespa.hosted.controller.api.integration.athenz.HostedAthenzIdentities.SCREWDRIVER_DOMAIN;

/**
 * A security filter protects all controller apis.
 *
 * @author bjorncs
 */
@After("com.yahoo.vespa.hosted.controller.athenz.filter.UserAuthWithAthenzPrincipalFilter")
@Provides("ControllerAuthorizationFilter")
public class ControllerAuthorizationFilter extends CorsRequestFilterBase {

    private static final List<Method> WHITELISTED_METHODS = Arrays.asList(GET, OPTIONS, HEAD);

    private static final Logger log = Logger.getLogger(ControllerAuthorizationFilter.class.getName());

    private final AthenzClientFactory clientFactory;
    private final TenantController tenantController;

    @Inject
    public ControllerAuthorizationFilter(AthenzClientFactory clientFactory,
                                         Controller controller,
                                         CorsFilterConfig corsConfig) {
        super(corsConfig);
        this.clientFactory = clientFactory;
        this.tenantController = controller.tenants();
    }

    ControllerAuthorizationFilter(AthenzClientFactory clientFactory,
                                  TenantController tenantController,
                                  Set<String> allowedUrls) {
        super(allowedUrls);
        this.clientFactory = clientFactory;
        this.tenantController = tenantController;
    }

    // NOTE: Be aware of the ordering of the path pattern matching. Semantics may change if the patterns are evaluated
    //       in different order.
    @Override
    public Optional<ErrorResponse> filter(DiscFilterRequest request) {
        Method method = getMethod(request);
        if (isWhiteListedMethod(method)) return Optional.empty();

        try {
            Path path = new Path(request.getRequestURI());
            AthenzPrincipal principal = getPrincipalOrThrow(request);
            if (isWhiteListedOperation(path, method)) {
                // no authz check
            } else if (isHostedOperatorOperation(path, method)) {
                verifyIsHostedOperator(principal);
            } else if (isTenantAdminOperation(path, method)) {
                verifyIsTenantAdmin(principal, getTenantName(path));
            } else if (isTenantPipelineOperation(path, method)) {
                verifyIsTenantPipelineOperator(principal, getTenantName(path), getApplicationName(path));
            } else {
                throw new ForbiddenException("No access control is explicitly declared for this api.");
            }
            return Optional.empty();
        } catch (WebApplicationException e) {
            int statusCode = e.getResponse().getStatus();
            String errorMessage = e.getMessage();
            log.log(LogLevel.WARNING, String.format("Access denied(%d): %s", statusCode, errorMessage), e);
            return Optional.of(new ErrorResponse(statusCode, errorMessage));
        }
    }

    private static boolean isWhiteListedMethod(Method method) {
        return WHITELISTED_METHODS.contains(method);
    }

    private static boolean isWhiteListedOperation(Path path, Method method) {
        return path.matches("/application/v4/user") && method == PUT || // Create user tenant
               path.matches("/application/v4/tenant/{tenant}") && method == POST; // Create tenant
    }

    private static boolean isHostedOperatorOperation(Path path, Method method) {
        if (isWhiteListedOperation(path, method)) return false;
        return path.matches("/application/v4/tenant/{tenant}/application/{application}/deploying") ||
                path.matches("/controller/v1/{*}") ||
                path.matches("/provision/v2/{*}") ||
                path.matches("/screwdriver/v1/trigger/tenant/{*}") ||
                path.matches("/zone/v2/{*}");
    }

    private static boolean isTenantAdminOperation(Path path, Method method) {
        if (isHostedOperatorOperation(path, method)) return false;
        return path.matches("/application/v4/tenant/{tenant}") ||
                path.matches("/application/v4/tenant/{tenant}/application/{application}") ||
                path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/dev/{*}") ||
                path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/perf/{*}") ||
                path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/instance/{instance}/global-rotation/override");
    }

    private static boolean isTenantPipelineOperation(Path path, Method method) {
        if (isTenantAdminOperation(path, method)) return false;
        return path.matches("/application/v4/tenant/{tenant}/application/{application}/jobreport") ||
                path.matches("/application/v4/tenant/{tenant}/application/{application}/promote") ||
                path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/prod/{*}") ||
                path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/test/{*}") ||
                path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/staging/{*}");
    }

    private void verifyIsHostedOperator(AthenzPrincipal principal) {
        if (!isHostedOperator(principal.getIdentity())) {
            throw new ForbiddenException("Vespa operator role required");
        }
    }

    private boolean isHostedOperator(AthenzIdentity identity) {
        return clientFactory.createZmsClientWithServicePrincipal()
                .hasHostedOperatorAccess(identity);
    }

    private void verifyIsTenantAdmin(AthenzPrincipal principal, TenantName name) {
        tenantController.tenant(name)
                .ifPresent(tenant -> {
                    if (!isTenantAdmin(principal.getIdentity(), tenant)) {
                        throw new ForbiddenException("Tenant admin or Vespa operator role required");
                    }
                });
    }

    private boolean isTenantAdmin(AthenzIdentity identity, Tenant tenant) {
        if (tenant instanceof AthenzTenant) {
            return clientFactory.createZmsClientWithServicePrincipal()
                                .hasTenantAdminAccess(identity, ((AthenzTenant) tenant).domain());
        } else if (tenant instanceof UserTenant) {
            if (!(identity instanceof AthenzUser)) {
                return false;
            }
            AthenzUser user = (AthenzUser) identity;
            return ((UserTenant) tenant).is(user.getName());
        }
        throw new InternalServerErrorException("Unknown tenant type: " + tenant.getClass().getSimpleName());
    }

    private void verifyIsTenantPipelineOperator(AthenzPrincipal principal,
                                                TenantName name,
                                                ApplicationName application) {
        tenantController.tenant(name)
                .ifPresent(tenant -> verifyIsTenantPipelineOperator(principal.getIdentity(), tenant, application));
    }

    private void verifyIsTenantPipelineOperator(AthenzIdentity identity, Tenant tenant, ApplicationName application) {
        if (isHostedOperator(identity)) return;

        AthenzDomain principalDomain = identity.getDomain();
        if (!principalDomain.equals(SCREWDRIVER_DOMAIN)) {
            throw new ForbiddenException(String.format(
                    "'%s' is not a Screwdriver identity. Only Screwdriver is allowed to deploy to this environment.",
                    identity.getFullName()));
        }

        // NOTE: no fine-grained deploy authorization for non-Athenz tenants
        if (tenant instanceof AthenzTenant) {
            AthenzDomain tenantDomain = ((AthenzTenant) tenant).domain();
            if (!hasDeployerAccess(identity, tenantDomain, application)) {
                throw new ForbiddenException(String.format(
                        "'%1$s' does not have access to '%2$s'. " +
                                "Either the application has not been created at Vespa dashboard or " +
                                "'%1$s' is not added to the application's deployer role in Athenz domain '%3$s'.",
                        identity.getFullName(), application.value(), tenantDomain.getName()));
            }
        }
    }

    private boolean hasDeployerAccess(AthenzIdentity identity, AthenzDomain tenantDomain, ApplicationName application) {
        try {
            return clientFactory.createZmsClientWithServicePrincipal()
                    .hasApplicationAccess(
                            identity,
                            ApplicationAction.deploy,
                            tenantDomain,
                            new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(application.value()));
        } catch (ZmsException e) {
            throw new InternalServerErrorException("Failed to authorize operation:  (" + e.getMessage() + ")", e);
        }
    }

    private static TenantName getTenantName(Path path) {
        if (!path.matches("/application/v4/tenant/{tenant}/{*}"))
            throw new InternalServerErrorException("Unable to handle path: " + path.asString());
        return TenantName.from(path.get("tenant"));
    }

    private static ApplicationName getApplicationName(Path path) {
        if (!path.matches("/application/v4/tenant/{tenant}/application/{application}/{*}"))
            throw new InternalServerErrorException("Unable to handle path: " + path.asString());
        return ApplicationName.from(path.get("application"));
    }

    private static Method getMethod(DiscFilterRequest request) {
        return Method.valueOf(request.getMethod().toUpperCase());
    }

    private static AthenzPrincipal getPrincipalOrThrow(DiscFilterRequest request) {
        return getPrincipal(request)
                .orElseThrow(() -> new NotAuthorizedException("User not authenticated"));
    }

    private static Optional<AthenzPrincipal> getPrincipal(DiscFilterRequest request) {
        return Optional.ofNullable(request.getUserPrincipal())
                .map(AthenzPrincipal.class::cast);
    }

}
