// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.TenantType;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsException;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import java.security.Principal;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.controller.api.integration.athenz.HostedAthenzIdentities.SCREWDRIVER_DOMAIN;
import static com.yahoo.vespa.hosted.controller.restapi.application.Authorizer.environmentRequiresAuthorization;

/**
 * @author bjorncs
 * @author gjoranv
 */
public class DeployAuthorizer {

    private static final Logger log = Logger.getLogger(DeployAuthorizer.class.getName());

    private final ZoneRegistry zoneRegistry;
    private final AthenzClientFactory athenzClientFactory;

    public DeployAuthorizer(ZoneRegistry zoneRegistry, AthenzClientFactory athenzClientFactory) {
        this.zoneRegistry = zoneRegistry;
        this.athenzClientFactory = athenzClientFactory;
    }

    public void throwIfUnauthorizedForDeploy(Principal principal,
                                             Environment environment,
                                             Tenant tenant,
                                             ApplicationId applicationId,
                                             Optional<ApplicationPackage> applicationPackage) {
        // Validate that domain in identity configuration (deployment.xml) is same as tenant domain
        applicationPackage.map(ApplicationPackage::deploymentSpec).flatMap(DeploymentSpec::athenzDomain)
                          .ifPresent(identityDomain -> {
            AthenzDomain tenantDomain = tenant.getAthensDomain().orElseThrow(() -> new IllegalArgumentException("Identity provider only available to Athenz onboarded tenants"));
            if (! Objects.equals(tenantDomain.getName(), identityDomain.value())) {
                throw new ForbiddenException(
                        String.format(
                                "Athenz domain in deployment.xml: [%s] must match tenant domain: [%s]",
                                identityDomain.value(),
                                tenantDomain.getName()
                        ));
            }
        });

        if (!environmentRequiresAuthorization(environment)) {
            return;
        }

        if (principal == null) {
            throw loggedUnauthorizedException("Principal not authenticated!");
        }

        if (!(principal instanceof AthenzPrincipal)) {
            throw loggedUnauthorizedException(
                    "Principal '%s' of type '%s' is not an Athenz principal, which is required for production deployments.",
                    principal.getName(), principal.getClass().getSimpleName());
        }

        AthenzPrincipal athenzPrincipal = (AthenzPrincipal) principal;
        AthenzDomain principalDomain = athenzPrincipal.getDomain();

        if (!principalDomain.equals(SCREWDRIVER_DOMAIN)) {
            throw loggedForbiddenException(
                    "Principal '%s' is not a Screwdriver principal. Excepted principal with Athenz domain '%s', got '%s'.",
                    principal.getName(), SCREWDRIVER_DOMAIN.getName(), principalDomain.getName());
        }

        if (tenant.tenantType() == TenantType.USER) {
            throw loggedForbiddenException("User tenants are only allowed to deploy to 'dev' and 'perf' environment");
        }

        // NOTE: no fine-grained deploy authorization for non-Athenz tenants
        if (tenant.isAthensTenant()) {
            AthenzDomain tenantDomain = tenant.getAthensDomain().get();
            if (!hasDeployAccessToAthenzApplication(athenzPrincipal, tenantDomain, applicationId)) {
                throw loggedForbiddenException(
                        "Screwdriver principal '%1$s' does not have deploy access to '%2$s'. " +
                        "Either the application has not been created at " + zoneRegistry.getDashboardUri() + " or " +
                        "'%1$s' is not added to the application's deployer role in Athenz domain '%3$s'.",
                        athenzPrincipal.getIdentity().getFullName(), applicationId, tenantDomain.getName());
            }
        }
    }

    private static ForbiddenException loggedForbiddenException(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        log.info(formattedMessage);
        return new ForbiddenException(formattedMessage);
    }

    private static NotAuthorizedException loggedUnauthorizedException(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        log.info(formattedMessage);
        return new NotAuthorizedException(formattedMessage);
    }

    private boolean hasDeployAccessToAthenzApplication(AthenzPrincipal principal, AthenzDomain domain, ApplicationId applicationId) {
        try {
            return athenzClientFactory.createZmsClientWithServicePrincipal()
                    .hasApplicationAccess(
                            principal.getIdentity(),
                            ApplicationAction.deploy,
                            domain,
                            new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(applicationId.application().value()));
        } catch (ZmsException e) {
            throw loggedForbiddenException(
                    "Failed to authorize deployment through Athenz. If this problem persists, " +
                            "please create ticket at yo/vespa-support. (" + e.getMessage() + ")");
        }
    }
}
