// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Payload;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.client.zms.ZmsClientException;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities.SCREWDRIVER_DOMAIN;

/**
 * Enriches the request principal with roles from Athenz, if an AthenzPrincipal is set on the request.
 *
 * @author jonmv
 */
public class AthenzRoleFilter extends JsonSecurityRequestFilterBase {

    private static final Logger logger = Logger.getLogger(AthenzRoleFilter.class.getName());

    private final AthenzFacade athenz;
    private final TenantController tenants;
    private final ExecutorService executor;
    private final ZoneRegistry zones;

    @Inject
    public AthenzRoleFilter(AthenzClientFactory athenzClientFactory, Controller controller) {
        this.athenz = new AthenzFacade(athenzClientFactory);
        this.tenants = controller.tenants();
        this.executor = Executors.newCachedThreadPool();
        this.zones = controller.zoneRegistry();
    }

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest request) {
        try {
            if (request.getUserPrincipal() instanceof AthenzPrincipal principal) {
                Optional<DecodedJWT> oktaAt = Optional.ofNullable((String) request.getAttribute("okta.access-token")).map(JWT::decode);
                Optional<X509Certificate> cert = request.getClientCertificateChain().stream().findFirst();
                Instant issuedAt = cert.map(X509Certificate::getNotBefore)
                        .or(() -> oktaAt.map(Payload::getIssuedAt))
                        .map(Date::toInstant).orElse(Instant.EPOCH);
                Instant expireAt = cert.map(X509Certificate::getNotAfter)
                        .or(() -> oktaAt.map(Payload::getExpiresAt))
                        .map(Date::toInstant).orElse(Instant.MAX);
                request.setAttribute(SecurityContext.ATTRIBUTE_NAME,
                                     new SecurityContext(principal, roles(principal, request.getUri()), issuedAt, expireAt));
            }
        }
        catch (Exception e) {
            logger.log(Level.INFO, () -> "Exception mapping Athenz principal to roles: " + Exceptions.toMessageString(e));
            return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Access denied"));
        }
        return Optional.empty();
    }

    Set<Role> roles(AthenzPrincipal principal, URI uri) throws Exception {
        Path path = new Path(uri);

        path.matches("/application/v4/tenant/{tenant}/{*}");
        Optional<Tenant> tenant = Optional.ofNullable(path.get("tenant")).map(TenantName::from).flatMap(tenants::get);

        path.matches("/application/v4/tenant/{tenant}/application/{application}/{*}");
        Optional<ApplicationName> application = Optional.ofNullable(path.get("application")).map(ApplicationName::from);

        final Optional<ZoneId> zone;
        if (path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/environment/{environment}/region/{region}/{*}")) {
            zone = Optional.of(ZoneId.from(path.get("environment"), path.get("region")));
        } else if(path.matches("/application/v4/tenant/{tenant}/application/{application}/environment/{environment}/region/{region}/{*}")) {
            zone = Optional.of(ZoneId.from(path.get("environment"), path.get("region")));
        } else if(path.matches("/application/v4/tenant/{tenant}/application/{application}/instance/{instance}/deploy/{jobname}")) {
            zone = Optional.of(JobType.fromJobName(path.get("jobname"), zones).zone());
        } else {
            zone = Optional.empty();
        }

        AthenzIdentity identity = principal.getIdentity();

        Set<Role> roleMemberships = new CopyOnWriteArraySet<>();
        List<Future<?>> futures = new ArrayList<>();

        futures.add(executor.submit(() -> {
            if (athenz.hasHostedOperatorAccess(identity))
                roleMemberships.add(Role.hostedOperator());
        }));

        futures.add(executor.submit(() -> {
            if (athenz.hasHostedSupporterAccess(identity))
                roleMemberships.add(Role.hostedSupporter());
        }));

        futures.add(executor.submit(() -> {
            // Add all tenants that are accessible for this request
            athenz.accessibleTenants(tenants.asList(), new Credentials(principal))
                  .forEach(accessibleTenant -> roleMemberships.add(Role.athenzTenantAdmin(accessibleTenant.name())));
        }));

        if (     identity.getDomain().equals(SCREWDRIVER_DOMAIN)
            &&   application.isPresent()
            &&   tenant.isPresent())
            futures.add(executor.submit(() -> {
                if (   tenant.get().type() == Tenant.Type.athenz
                    && hasDeployerAccess(identity, ((AthenzTenant) tenant.get()).domain(), application.get(), zone))
                    roleMemberships.add(Role.buildService(tenant.get().name(), application.get()));
            }));

        if (identity instanceof AthenzUser
            && zone.isPresent()
            && tenant.isPresent()
            && application.isPresent()) {
            ZoneId z = zone.get();
            futures.add(executor.submit(() -> {
                if (tenant.get().type() == Tenant.Type.athenz
                    && canDeployToManualZones(identity, ((AthenzTenant) tenant.get()).domain(), application.get(), z))
                    roleMemberships.add(Role.hostedDeveloper(tenant.get().name()));
            }));
        }

        futures.add(executor.submit(() -> {
            if (athenz.hasSystemFlagsAccess(identity, /*dryrun*/false))
                roleMemberships.add(Role.systemFlagsDeployer());
        }));

        futures.add(executor.submit(() -> {
            if (athenz.hasPaymentCallbackAccess(identity))
                roleMemberships.add(Role.paymentProcessor());
        }));

        futures.add(executor.submit(() -> {
            if (athenz.hasAccountingAccess(identity))
                roleMemberships.add(Role.hostedAccountant());
        }));

        // Run last request in handler thread to avoid creating extra thread.
        if (athenz.hasSystemFlagsAccess(identity, /*dryrun*/true))
            roleMemberships.add(Role.systemFlagsDryrunner());

        for (Future<?> future : futures)
            future.get(30, TimeUnit.SECONDS);

        logger.log(Level.FINE, () -> "Roles for principal (" + principal.getName() + "): " +
                                     roleMemberships.stream().map(role -> role.definition().name()).collect(Collectors.joining()));

        return roleMemberships.isEmpty()
                ? Set.of(Role.everyone())
                : Set.copyOf(roleMemberships);
    }

    @Override
    public void deconstruct() {
        try {
            executor.shutdown();
            if ( ! executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if ( ! executor.awaitTermination(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("Failed to shut down executor 40 seconds");
            }
        }
        catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while shutting down executor", e);
        }
    }

    private boolean hasDeployerAccess(AthenzIdentity identity, AthenzDomain tenantDomain, ApplicationName application, Optional<ZoneId> zone) {
        try {
            return athenz.hasApplicationAccess(identity,
                                               ApplicationAction.deploy,
                                               tenantDomain,
                                               application,
                                               zone);
        } catch (ZmsClientException e) {
            throw new RuntimeException("Failed to authorize operation:  (" + e.getMessage() + ")", e);
        }
    }

    private boolean canDeployToManualZones(AthenzIdentity identity, AthenzDomain tenantDomain, ApplicationName application, ZoneId zone) {
        if (! zone.environment().isManuallyDeployed()) return false;
        try {
            return athenz.hasApplicationAccess(identity, ApplicationAction.deploy, tenantDomain, application, Optional.of(zone));
        } catch (ZmsClientException e) {
            throw new RuntimeException("Failed to authorize operation:  (" + e.getMessage() + ")", e);
        }
    }

}
