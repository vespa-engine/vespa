package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.client.zms.ZmsClientException;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.role.Role;
import com.yahoo.vespa.hosted.controller.role.RoleMembership;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;

import javax.ws.rs.InternalServerErrorException;
import java.security.Principal;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities.SCREWDRIVER_DOMAIN;

/**
 * Translates Athenz principals to role memberships for use in access control.
 *
 * @author tokle
 * @author mpolden
 */
public class AthenzRoleResolver implements RoleMembership.Resolver {

    private final AthenzFacade athenz;
    private final TenantController tenants;
    private final SystemName system;

    @Inject
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
