// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserGroup;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzIdentity;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzUser;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.NToken;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.common.ContextAttributes;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.SecurityContext;
import java.util.Optional;
import java.util.logging.Logger;


/**
 * @author Stian Kristoffersen
 * @author Tony Vaagenes
 * @author bjorncs
 */
// TODO: Make this an interface
public class Authorizer {

    private static final Logger log = Logger.getLogger(Authorizer.class.getName());

    // Must be kept in sync with bouncer filter configuration.
    private static final String VESPA_HOSTED_ADMIN_ROLE = "10707.A";

    private final Controller controller;
    private final AthenzClientFactory athenzClientFactory;
    private final EntityService entityService;

    public Authorizer(Controller controller, EntityService entityService, AthenzClientFactory athenzClientFactory) {
        this.controller = controller;
        this.athenzClientFactory = athenzClientFactory;
        this.entityService = entityService;
    }

    public void throwIfUnauthorized(TenantId tenantId, HttpRequest request) throws ForbiddenException {
        if (isReadOnlyMethod(request.getMethod().name())) return;
        if (isSuperUser(request)) return;

        Optional<Tenant> tenant = controller.tenants().tenant(tenantId);
        if ( ! tenant.isPresent()) return;

        AthenzIdentity identity = getIdentity(request);
        if (isTenantAdmin(identity, tenant.get())) return;

        throw loggedForbiddenException("User " + identity.getFullName() + " does not have write access to tenant " + tenantId);
    }

    public AthenzIdentity getIdentity(HttpRequest request) {
        return getPrincipal(request).getIdentity();
    }

    /** Returns the principal or throws forbidden */ // TODO: Avoid REST exceptions
    public AthenzPrincipal getPrincipal(HttpRequest request) {
        return getPrincipalIfAny(request).orElseThrow(() -> Authorizer.loggedForbiddenException("User is not authenticated"));
    }

    /** Returns the principal if there is any */
    public Optional<AthenzPrincipal> getPrincipalIfAny(HttpRequest request) {
        return securityContextOf(request)
                .map(SecurityContext::getUserPrincipal)
                .map(AthenzPrincipal.class::cast);
    }

    public Optional<NToken> getNToken(HttpRequest request) {
        return getPrincipalIfAny(request).flatMap(AthenzPrincipal::getNToken);
    }

    public boolean isSuperUser(HttpRequest request) {
        // TODO Replace check with membership of a dedicated 'hosted Vespa super-user' role in Vespa's Athenz domain
        return isMemberOfVespaBouncerGroup(request);
    }

    private static ForbiddenException loggedForbiddenException(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        log.info(formattedMessage);
        return new ForbiddenException(formattedMessage);
    }

    private boolean isTenantAdmin(AthenzIdentity identity, Tenant tenant) {
        switch (tenant.tenantType()) {
            case ATHENS:
                return isAthenzTenantAdmin(identity, tenant.getAthensDomain().get());
            case OPSDB: {
                if (!(identity instanceof AthenzUser)) {
                    return false;
                }
                AthenzUser user = (AthenzUser) identity;
                return isGroupMember(user.getUserId(), tenant.getUserGroup().get());
            }
            case USER: {
                if (!(identity instanceof AthenzUser)) {
                    return false;
                }
                AthenzUser user = (AthenzUser) identity;
                return isUserTenantOwner(tenant.getId(), user.getUserId());
            }
        }
        throw new IllegalArgumentException("Unknown tenant type: " + tenant.tenantType());
    }

    private boolean isAthenzTenantAdmin(AthenzIdentity athenzIdentity, AthenzDomain tenantDomain) {
        return athenzClientFactory.createZmsClientWithServicePrincipal()
                .hasTenantAdminAccess(athenzIdentity, tenantDomain);
    }

    public boolean isAthenzDomainAdmin(AthenzIdentity identity, AthenzDomain tenantDomain) {
        return athenzClientFactory.createZmsClientWithServicePrincipal()
                .isDomainAdmin(identity, tenantDomain);
    }

    public boolean isGroupMember(UserId userId, UserGroup userGroup) {
        return entityService.isGroupMember(userId, userGroup);
    }

    private static boolean isUserTenantOwner(TenantId tenantId, UserId userId) {
        return tenantId.equals(userId.toTenantId());
    }

    public static boolean environmentRequiresAuthorization(Environment environment) {
        return environment != Environment.dev && environment != Environment.perf;
    }

    private static boolean isReadOnlyMethod(String method) {
        return method.equals(HttpMethod.GET) || method.equals(HttpMethod.HEAD) || method.equals(HttpMethod.OPTIONS);
    }

    @Deprecated
    // TODO Remove this method. Stop using Bouncer for authorization and use Athenz instead
    private boolean isMemberOfVespaBouncerGroup(HttpRequest request) {
        Optional<SecurityContext> securityContext = securityContextOf(request);
        if ( ! securityContext.isPresent() ) throw Authorizer.loggedForbiddenException("User is not authenticated");
        return securityContext.get().isUserInRole(Authorizer.VESPA_HOSTED_ADMIN_ROLE);
    }

    protected Optional<SecurityContext> securityContextOf(HttpRequest request) {
        return Optional.ofNullable((SecurityContext)request.getJDiscRequest().context().get(ContextAttributes.SECURITY_CONTEXT_ATTRIBUTE));
    }

}
