// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.AthenzUtils;
import com.yahoo.vespa.hosted.controller.athenz.ZmsException;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import java.security.Principal;
import java.util.logging.Logger;

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
                                             ApplicationId applicationId) {
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

        if (!principalDomain.equals(AthenzUtils.SCREWDRIVER_DOMAIN)) {
            throw loggedForbiddenException(
                    "Principal '%s' is not a Screwdriver principal. Excepted principal with Athenz domain '%s', got '%s'.",
                    principal.getName(), AthenzUtils.SCREWDRIVER_DOMAIN.id(), principalDomain.id());
        }

        // NOTE: no fine-grained deploy authorization for non-Athenz tenants
        if (tenant.isAthensTenant()) {
            AthenzDomain tenantDomain = tenant.getAthensDomain().get();
            if (!hasDeployAccessToAthenzApplication(athenzPrincipal, tenantDomain, applicationId)) {
                throw loggedForbiddenException(
                        "Screwdriver principal '%1$s' does not have deploy access to '%2$s'. " +
                        "Either the application has not been created at " + zoneRegistry.getDashboardUri() + " or " +
                        "'%1$s' is not added to the application's deployer role in Athenz domain '%3$s'.",
                        athenzPrincipal.toYRN(), applicationId, tenantDomain.id());
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
                            principal,
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
