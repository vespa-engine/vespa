// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.AthenzUtils;
import com.yahoo.vespa.hosted.controller.athenz.ZmsException;
import com.yahoo.vespa.hosted.controller.restapi.filter.UnauthenticatedUserPrincipal;

import javax.ws.rs.ForbiddenException;
import java.security.Principal;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.controller.restapi.application.Authorizer.environmentRequiresAuthorization;
import static com.yahoo.vespa.hosted.controller.restapi.application.Authorizer.isScrewdriverPrincipal;

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
        if (athenzCredentialsRequired(environment, tenant, applicationId, principal))
            checkAthenzCredentials(principal, tenant, applicationId);
    }

    // TODO: inline when deployment via ssh is removed
    private boolean athenzCredentialsRequired(Environment environment, Tenant tenant, ApplicationId applicationId, Principal principal) {
        if (!environmentRequiresAuthorization(environment))  return false;

        if (! isScrewdriverPrincipal(principal))
            throw loggedForbiddenException(
                    "Principal '%s' is not a screwdriver principal, and does not have deploy access to application '%s'",
                    principal.getName(), applicationId.toShortString());

        return tenant.isAthensTenant();
    }


    // TODO: inline when deployment via ssh is removed
    private void checkAthenzCredentials(Principal principal, Tenant tenant, ApplicationId applicationId) {
        AthenzDomain domain = tenant.getAthensDomain().get();
        if (! (principal instanceof AthenzPrincipal))
            throw loggedForbiddenException("Principal '%s' is not authenticated.", principal.getName());

        AthenzPrincipal athensPrincipal = (AthenzPrincipal)principal;
        if ( ! hasDeployAccessToAthenzApplication(athensPrincipal, domain, applicationId))
            throw loggedForbiddenException(
                    "Screwdriver principal '%1$s' does not have deploy access to '%2$s'. " +
                    "Either the application has not been created at " + zoneRegistry.getDashboardUri() + " or " +
                    "'%1$s' is not added to the application's deployer role in Athens domain '%3$s'.",
                    athensPrincipal, applicationId, tenant.getAthensDomain().get());
    }

    private static ForbiddenException loggedForbiddenException(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        log.info(formattedMessage);
        return new ForbiddenException(formattedMessage);
    }

    /**
     * @deprecated Only usable for ssh. Use the method that takes Principal instead of UserId and screwdriverId.
     */
    @Deprecated
    public void throwIfUnauthorizedForDeploy(Environment environment,
                                             UserId userId,
                                             Tenant tenant,
                                             ApplicationId applicationId,
                                             Optional<ScrewdriverId> optionalScrewdriverId) {
        Principal principal = new UnauthenticatedUserPrincipal(userId.id());

        if (athenzCredentialsRequired(environment, tenant, applicationId, principal)) {
            ScrewdriverId screwdriverId = optionalScrewdriverId.orElseThrow(
                    () -> loggedForbiddenException("Screwdriver id must be provided when deploying from Screwdriver."));
            principal = AthenzUtils.createPrincipal(screwdriverId);
            checkAthenzCredentials(principal, tenant, applicationId);
        }
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
