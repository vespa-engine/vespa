// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.cors.CorsFilterConfig;
import com.yahoo.jdisc.http.filter.security.cors.CorsRequestFilterBase;
import com.yahoo.log.LogLevel;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.client.zms.ZmsClientException;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.role.Action;
import com.yahoo.vespa.hosted.controller.role.Context;
import com.yahoo.vespa.hosted.controller.role.Role;
import com.yahoo.vespa.hosted.controller.role.RoleMembership;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.yolean.chain.After;
import com.yahoo.yolean.chain.Provides;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.WebApplicationException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities.SCREWDRIVER_DOMAIN;

/**
 * A security filter protects all controller apis.
 *
 * @author bjorncs
 */
@After("com.yahoo.vespa.hosted.controller.athenz.filter.UserAuthWithAthenzPrincipalFilter")
@Provides("ControllerAuthorizationFilter")
public class ControllerAuthorizationFilter extends CorsRequestFilterBase {

    private static final Logger log = Logger.getLogger(ControllerAuthorizationFilter.class.getName());

    private final AthenzFacade athenz;
    private final Controller controller;

    @Inject
    public ControllerAuthorizationFilter(AthenzClientFactory clientFactory,
                                         Controller controller,
                                         CorsFilterConfig corsConfig) {
        this(clientFactory, controller, Set.copyOf(corsConfig.allowedUrls()));
    }

    ControllerAuthorizationFilter(AthenzClientFactory clientFactory,
                                  Controller controller,
                                  Set<String> allowedUrls) {
        super(allowedUrls);
        this.athenz = new AthenzFacade(clientFactory);;
        this.controller = controller;
    }

    @Override
    public Optional<ErrorResponse> filterRequest(DiscFilterRequest request) {
        try {
            Principal principal = request.getUserPrincipal();
            if (principal == null)
                return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));

            Action action = Action.from(HttpRequest.Method.valueOf(request.getMethod()));

            // Avoid expensive lookups when request is always legal.
            if (RoleMembership.everyoneIn(controller.system()).allows(action, request.getRequestURI()))
                return Optional.empty();

            RoleMembership roles = new AthenzRoleResolver(athenz, controller).membership(principal,
                                                                                         Optional.of(request.getRequestURI()));
            if (roles.allows(action, request.getRequestURI()))
                return Optional.empty();

            return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));
        }
        catch (WebApplicationException e) {
            int statusCode = e.getResponse().getStatus();
            String errorMessage = e.getMessage();
            log.log(LogLevel.WARNING, String.format("Access denied (%d): %s", statusCode, errorMessage));
            return Optional.of(new ErrorResponse(statusCode, errorMessage));
        }
    }

    // TODO: Pull class up and resolve roles from roles defined in Athenz
    private static class AthenzRoleResolver implements RoleMembership.Resolver {

        private final AthenzFacade athenz;
        private final TenantController tenants;
        private final SystemName system;

        public AthenzRoleResolver(AthenzFacade athenz, Controller controller) {
            this.athenz = athenz;
            this.tenants = controller.tenants();
            this.system = controller.system();
        }

        private boolean isTenantAdmin(AthenzIdentity identity, Tenant tenant) {
            if (tenant instanceof AthenzTenant) {
                return athenz.hasTenantAdminAccess(identity, ((AthenzTenant) tenant).domain());
            } else if (tenant instanceof UserTenant) {
                if (!(identity instanceof AthenzUser)) {
                    return false;
                }
                AthenzUser user = (AthenzUser) identity;
                return ((UserTenant) tenant).is(user.getName()) || isHostedOperator(identity);
            }
            throw new InternalServerErrorException("Unknown tenant type: " + tenant.getClass().getSimpleName());
        }

        private boolean hasDeployerAccess(AthenzIdentity identity, AthenzDomain tenantDomain, ApplicationName application) {
            try {
                return athenz.hasApplicationAccess(identity,
                                                   ApplicationAction.deploy,
                                                   tenantDomain,
                                                   application);
            } catch (ZmsClientException e) {
                throw new InternalServerErrorException("Failed to authorize operation:  (" + e.getMessage() + ")", e);
            }
        }

        private boolean isHostedOperator(AthenzIdentity identity) {
            return athenz.hasHostedOperatorAccess(identity);
        }

        @Override
        public RoleMembership membership(Principal principal, Optional<String> uriPath) {
            if ( ! (principal instanceof AthenzPrincipal))
                throw new IllegalStateException("Expected an AthenzPrincipal to be set on the request.");

            Path path = new Path(uriPath.orElseThrow(() -> new IllegalArgumentException("This resolver needs the request path.")));

            path.matches("/application/v4/tenant/{tenant}/{*}");
            Optional<Tenant> tenant = Optional.ofNullable(path.get("tenant")).map(TenantName::from).flatMap(tenants::get);

            path.matches("/application/v4/tenant/{tenant}/application/{application}/{*}");
            Optional<ApplicationName> application = Optional.ofNullable(path.get("application")).map(ApplicationName::from);

            AthenzIdentity identity = ((AthenzPrincipal) principal).getIdentity();

            RoleMembership.Builder memberships = RoleMembership.in(system);
            if (isHostedOperator(identity)) {
                memberships.add(Role.hostedOperator);
            }
            if (tenant.isPresent() && isTenantAdmin(identity, tenant.get())) {
                memberships.add(Role.tenantAdmin).limitedTo(tenant.get().name());
            }
            AthenzDomain principalDomain = identity.getDomain();
            if (principalDomain.equals(SCREWDRIVER_DOMAIN)) {
                if (application.isPresent() && tenant.isPresent()) {
                    // NOTE: Only fine-grained deploy authorization for Athenz tenants
                    if (tenant.get() instanceof AthenzTenant) {
                        AthenzDomain tenantDomain = ((AthenzTenant) tenant.get()).domain();
                        if (hasDeployerAccess(identity, tenantDomain, application.get())) {
                            memberships.add(Role.tenantPipelineOperator).limitedTo(tenant.get().name(), application.get());
                        }
                    }
                    else {
                        memberships.add(Role.tenantPipelineOperator).limitedTo(tenant.get().name(), application.get());
                    }
                }
            }
            memberships.add(Role.everyone);
            return memberships.build();
        }

    }

    private static Optional<AthenzPrincipal> principalFrom(DiscFilterRequest request) {
        return Optional.ofNullable(request.getUserPrincipal())
                       .map(AthenzPrincipal.class::cast);
    }

}
