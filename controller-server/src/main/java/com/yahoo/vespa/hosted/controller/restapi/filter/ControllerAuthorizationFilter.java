// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
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
            Path path = new Path(request.getRequestURI());
            Optional<AthenzPrincipal> principal = principalFrom(request);
            Action action = Action.from(HttpRequest.Method.valueOf(request.getMethod()));
            AthenzRoleResolver resolver = new AthenzRoleResolver(principal, athenz, controller, path);
            RoleMembership roles = resolver.membership();
            if (!roles.allow(action, request.getRequestURI())) {
                throw new ForbiddenException("Access denied");
            }
            return Optional.empty();
        } catch (WebApplicationException e) {
            int statusCode = e.getResponse().getStatus();
            String errorMessage = e.getMessage();
            log.log(LogLevel.WARNING, String.format("Access denied (%d): %s", statusCode, errorMessage));
            return Optional.of(new ErrorResponse(statusCode, errorMessage));
        }
    }

    // TODO: Pull class up and resolve roles from roles defined in Athenz
    private static class AthenzRoleResolver implements RoleMembership.Resolver {

        private final Optional<AthenzPrincipal> principal;
        private final AthenzFacade athenz;
        private final TenantController tenants;
        private final Path path;
        private final SystemName system;

        public AthenzRoleResolver(Optional<AthenzPrincipal> principal, AthenzFacade athenz, Controller controller, Path path) {
            this.principal = principal;
            this.athenz = athenz;
            this.tenants = controller.tenants();
            this.path = path;
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
        public RoleMembership membership() {
            Optional<Tenant> tenant = tenant(path);
            Context context = context(tenant);
            Set<Context> contexts = Set.of(context);
            if (principal.isEmpty()) {
                return new RoleMembership(Map.of(Role.everyone, contexts));
            }
            if (isHostedOperator(principal.get().getIdentity())) {
                return new RoleMembership(Map.of(Role.hostedOperator, contexts));
            }
            if (tenant.isPresent() && isTenantAdmin(principal.get().getIdentity(), tenant.get())) {
                return new RoleMembership(Map.of(Role.tenantAdmin, contexts));
            }
            AthenzDomain principalDomain = principal.get().getIdentity().getDomain();
            if (principalDomain.equals(SCREWDRIVER_DOMAIN)) {
                // NOTE: Only fine-grained deploy authorization for Athenz tenants
                Optional<ApplicationName> application = context.application();
                if (application.isPresent() && tenant.isPresent() && tenant.get() instanceof AthenzTenant) {
                    AthenzDomain tenantDomain = ((AthenzTenant) tenant.get()).domain();
                    if (!hasDeployerAccess(principal.get().getIdentity(), tenantDomain, application.get())) {
                        throw new ForbiddenException(String.format(
                                "'%1$s' does not have access to '%2$s'. " +
                                "Either the application has not been created at Vespa dashboard or " +
                                "'%1$s' is not added to the application's deployer role in Athenz domain '%3$s'.",
                                principal.get().getIdentity().getFullName(), application.get().value(), tenantDomain.getName()));
                    }
                }
                return new RoleMembership(Map.of(Role.tenantPipelineOperator, contexts));
            }
            return new RoleMembership(Map.of(Role.everyone, contexts));
        }

        private Optional<Tenant> tenant(Path path) {
            if (!path.matches("/application/v4/tenant/{tenant}/{*}")) {
                return Optional.empty();
            }
            return tenants.get(TenantName.from(path.get("tenant")));
        }

        // TODO: Currently there's only one context for each role, but this will change
        private Context context(Optional<Tenant> tenant) {
            if (principal.isEmpty() || tenant.isEmpty()) {
                return Context.empty(system);
            }
            if (!isTenantAdmin(principal.get().getIdentity(), tenant.get())) {
                return Context.empty(system);
            }
            // TODO: Remove this. Current behaviour always allows tenant full access to all its applications, but with
            //       the new role setup, each role will include a complete context (tenant + app)
            Optional<ApplicationName> application = Optional.empty();
            if (path.matches("/application/v4/tenant/{tenant}/application/{application}/{*}")) {
                application = Optional.of(ApplicationName.from(path.get("application")));
            }
            return Context.of(tenant.get().name(), application, system);
        }
    }

    private static Optional<AthenzPrincipal> principalFrom(DiscFilterRequest request) {
        return Optional.ofNullable(request.getUserPrincipal())
                       .map(AthenzPrincipal.class::cast);
    }

}
