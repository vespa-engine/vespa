package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.cors.CorsFilterConfig;
import com.yahoo.jdisc.http.filter.security.cors.CorsRequestFilterBase;
import com.yahoo.log.LogLevel;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.client.zms.ZmsClientException;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.yolean.Exceptions;

import java.net.URI;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities.SCREWDRIVER_DOMAIN;

/**
 * Enriches the request principal with roles from Athenz.
 *
 * @author jonmv
 */
public class AthenzRoleFilter extends CorsRequestFilterBase { // TODO: No need for this super anyway.

    private static final Logger logger = Logger.getLogger(AthenzRoleFilter.class.getName());

    private final AthenzFacade athenz;
    private final TenantController tenants;

    @Inject
    public AthenzRoleFilter(CorsFilterConfig config, AthenzClientFactory athenzClientFactory, Controller controller) {
        super(Set.copyOf(config.allowedUrls()));
        this.athenz = new AthenzFacade(athenzClientFactory);
        this.tenants = controller.tenants();
    }

    @Override
    protected Optional<ErrorResponse> filterRequest(DiscFilterRequest request) {
        try {
            AthenzPrincipal athenzPrincipal = (AthenzPrincipal) request.getUserPrincipal();
            request.setAttribute(SecurityContext.ATTRIBUTE_NAME, new SecurityContext(athenzPrincipal,
                                                                                     roles(athenzPrincipal, request.getUri())));
            return Optional.empty();
        }
        catch (Exception e) {
            logger.log(LogLevel.DEBUG, () -> "Exception mapping Athenz principal to roles: " + Exceptions.toMessageString(e));
            return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, "Access denied"));
        }
    }

    Set<Role> roles(AthenzPrincipal principal, URI uri) {
        Path path = new Path(uri);

        path.matches("/application/v4/tenant/{tenant}/{*}");
        Optional<Tenant> tenant = Optional.ofNullable(path.get("tenant")).map(TenantName::from).flatMap(tenants::get);

        path.matches("/application/v4/tenant/{tenant}/application/{application}/{*}");
        Optional<ApplicationName> application = Optional.ofNullable(path.get("application")).map(ApplicationName::from);

        AthenzIdentity identity = principal.getIdentity();

        if (athenz.hasHostedOperatorAccess(identity))
            return Set.of(Role.hostedOperator());

        // A principal can be both tenant admin and tenantPipeline
        Set<Role> roleMemberships = new HashSet<>();
        if (tenant.isPresent() && isTenantAdmin(identity, tenant.get()))
            roleMemberships.add(Role.athenzTenantAdmin(tenant.get().name()));

        if (identity.getDomain().equals(SCREWDRIVER_DOMAIN) && application.isPresent() && tenant.isPresent())
            // NOTE: Only fine-grained deploy authorization for Athenz tenants
            if (   tenant.get().type() != Tenant.Type.athenz
                || hasDeployerAccess(identity, ((AthenzTenant) tenant.get()).domain(), application.get()))
                    roleMemberships.add(Role.tenantPipeline(tenant.get().name(), application.get()));

        return roleMemberships.isEmpty()
                ? Set.of(Role.everyone())
                : Set.copyOf(roleMemberships);
    }

    private boolean isTenantAdmin(AthenzIdentity identity, Tenant tenant) {
        switch (tenant.type()) {
            case athenz: return athenz.hasTenantAdminAccess(identity, ((AthenzTenant) tenant).domain());
            case user: return ((UserTenant) tenant).is(identity.getName()) || athenz.hasHostedOperatorAccess(identity);
            default: throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
        }
    }

    private boolean hasDeployerAccess(AthenzIdentity identity, AthenzDomain tenantDomain, ApplicationName application) {
        try {
            return athenz.hasApplicationAccess(identity,
                                               ApplicationAction.deploy,
                                               tenantDomain,
                                               application);
        } catch (ZmsClientException e) {
            throw new RuntimeException("Failed to authorize operation:  (" + e.getMessage() + ")", e);
        }
    }

}
