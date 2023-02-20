// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.restapi.RestApiException;
import com.yahoo.text.Text;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OAuthCredentials;
import com.yahoo.vespa.athenz.client.zms.RoleAction;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.athenz.client.zms.ZmsClientException;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.security.AccessControl;
import com.yahoo.vespa.hosted.controller.security.AthenzCredentials;
import com.yahoo.vespa.hosted.controller.security.AthenzTenantSpec;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.security.TenantSpec;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author bjorncs
 * @author jonmv
 */
public class AthenzFacade implements AccessControl {

    private static final Logger log = Logger.getLogger(AthenzFacade.class.getName());
    private final ZmsClient zmsClient;
    private final ZtsClient ztsClient;
    private final AthenzIdentity service;
    private final Function<AthenzIdentity, List<AthenzDomain>> userDomains;
    private final Predicate<AccessTuple> accessRights;

    @Inject
    public AthenzFacade(AthenzClientFactory factory) {
        this.zmsClient = factory.createZmsClient();
        this.ztsClient = factory.createZtsClient();
        this.service = factory.getControllerIdentity();
        this.userDomains = factory.cacheLookups()
                           ? CacheBuilder.newBuilder()
                                         .expireAfterWrite(10, TimeUnit.SECONDS)
                                         .build(CacheLoader.from(this::getUserDomains))::getUnchecked
                           : this::getUserDomains;
        this.accessRights = factory.cacheLookups()
                            ? CacheBuilder.newBuilder()
                                          .expireAfterWrite(10, TimeUnit.SECONDS)
                                          .build(CacheLoader.from(this::lookupAccess))::getUnchecked
                            : this::lookupAccess;
    }

    private List<AthenzDomain> getUserDomains(AthenzIdentity userIdentity) {
        return ztsClient.getTenantDomains(service, userIdentity, "admin");
    }

    @Override
    public Tenant createTenant(TenantSpec tenantSpec, Instant createdAt, Credentials credentials, List<Tenant> existing) {
        AthenzTenantSpec spec = (AthenzTenantSpec) tenantSpec;
        AthenzCredentials athenzCredentials = (AthenzCredentials) credentials;
        AthenzDomain domain = spec.domain();

        verifyIsDomainAdmin(athenzCredentials.user().getIdentity(), domain);

        Optional<Tenant> existingWithSameDomain = existing.stream()
                                                          .filter(tenant ->    tenant.type() == Tenant.Type.athenz
                                                                            && domain.equals(((AthenzTenant) tenant).domain()))
                                                          .findAny();

        AthenzTenant tenant = AthenzTenant.create(spec.tenant(),
                                                  domain,
                                                  spec.property(),
                                                  spec.propertyId(),
                                                  createdAt);

        if (existingWithSameDomain.isPresent()) { // Throw if domain is already taken.
            throw new IllegalArgumentException("Could not create tenant '" + spec.tenant().value() +
                                               "': The Athens domain '" +
                                               domain.getName() + "' is already connected to tenant '" +
                                               existingWithSameDomain.get().name().value() + "'");
        }
        else { // Create tenant resources in Athenz if domain is not already taken.
            log("createTenancy(tenantDomain=%s, service=%s)", domain, service);
            zmsClient.createTenancy(domain, service, athenzCredentials.oAuthCredentials());
        }

        return tenant;
    }

    @Override
    public Tenant updateTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing, List<Application> applications) {
        AthenzTenantSpec spec = (AthenzTenantSpec) tenantSpec;
        AthenzCredentials athenzCredentials = (AthenzCredentials) credentials;
        AthenzDomain newDomain = spec.domain();
        AthenzDomain oldDomain = athenzCredentials.domain();

        verifyIsDomainAdmin(athenzCredentials.user().getIdentity(), newDomain);

        Optional<Tenant> existingWithSameDomain = existing.stream()
                                                          .filter(tenant ->    tenant.type() == Tenant.Type.athenz
                                                                            && newDomain.equals(((AthenzTenant) tenant).domain()))
                                                          .findAny();
        Instant createdAt = existing.stream()
                .filter(tenant -> tenant.name().equals(spec.tenant()))
                .findAny().orElseThrow() // Should not happen, we assert that the tenant exists before the method is called
                .createdAt();

        Tenant tenant = AthenzTenant.create(spec.tenant(),
                                            newDomain,
                                            spec.property(),
                                            spec.propertyId(),
                                            createdAt);

        if (existingWithSameDomain.isPresent()) { // Throw if domain taken by someone else, or do nothing if taken by this tenant.
            if ( ! existingWithSameDomain.get().equals(tenant)) // Equality by name.
                throw new IllegalArgumentException("Could not create tenant '" + spec.tenant().value() +
                                                   "': The Athens domain '" +
                                                   newDomain.getName() + "' is already connected to tenant '" +
                                                   existingWithSameDomain.get().name().value() + "'");

            return tenant; // Short-circuit here if domain is still the same.
        }
        else { // Delete and recreate tenant, and optionally application, resources in Athenz otherwise.
            log("createTenancy(tenantDomain=%s, service=%s)", newDomain, service);
            zmsClient.createTenancy(newDomain, service, athenzCredentials.oAuthCredentials());
            for (Application application : applications)
                createApplication(newDomain, application.id().application(), athenzCredentials.oAuthCredentials());

            log("deleteTenancy(tenantDomain=%s, service=%s)", oldDomain, service);
            for (Application application : applications)
                deleteApplication(oldDomain, application.id().application(), athenzCredentials.oAuthCredentials());
            zmsClient.deleteTenancy(oldDomain, service, athenzCredentials.oAuthCredentials());
        }

        return tenant;
    }

    @Override
    public void deleteTenant(TenantName tenant, Credentials credentials) {
        AthenzCredentials athenzCredentials = (AthenzCredentials) credentials;
        AthenzDomain tenantDomain = athenzCredentials.domain();
        log("deleteTenancy(tenantDomain=%s, service=%s)", tenantDomain, service);
        try {
            zmsClient.deleteTenancy(tenantDomain, service, athenzCredentials.oAuthCredentials());
        } catch (ZmsClientException e) {
            if (e.getErrorCode() == 404) {
                log.log(Level.WARNING,
                        "Failed to cleanup tenant " + tenant.value() + " with domain '" + tenantDomain.getName()
                                + "' in Athenz due to non-existing tenant domain",
                        e);
            } else {
                throw e;
            }
        }
    }

    @Override
    public void createApplication(TenantAndApplicationId id, Credentials credentials) {
        AthenzCredentials athenzCredentials = (AthenzCredentials) credentials;
        createApplication(athenzCredentials.domain(), id.application(), athenzCredentials.oAuthCredentials());
    }

    private void createApplication(AthenzDomain domain, ApplicationName application, OAuthCredentials oAuthCredentials) {
        Set<RoleAction> tenantRoleActions = createTenantRoleActions();
        log("createProviderResourceGroup(" +
            "tenantDomain=%s, providerDomain=%s, service=%s, resourceGroup=%s, roleActions=%s)",
            domain, service.getDomain().getName(), service.getName(), application, tenantRoleActions);
        try {
            zmsClient.createProviderResourceGroup(domain, service, application.value(), tenantRoleActions, oAuthCredentials);
        }
        catch (ZmsClientException e) {
            if (e.getErrorCode() == com.yahoo.jdisc.Response.Status.FORBIDDEN)
                throw new RestApiException.Forbidden("Not authorized to create application", e);
            else
                throw e;
        }
    }

    @Override
    public void deleteApplication(TenantAndApplicationId id, Credentials credentials) {
        AthenzCredentials athenzCredentials = (AthenzCredentials) credentials;
        log("deleteProviderResourceGroup(tenantDomain=%s, providerDomain=%s, service=%s, resourceGroup=%s)",
            athenzCredentials.domain(), service.getDomain().getName(), service.getName(), id.application());
        try {
            zmsClient.deleteProviderResourceGroup(athenzCredentials.domain(), service, id.application().value(),
                    athenzCredentials.oAuthCredentials());
        } catch (ZmsClientException e) {
            if (e.getErrorCode() == 404) {
                log.log(Level.WARNING,
                        "Failed to cleanup application '" + id.serialized()
                                + "' in Athenz due to non-existing tenant domain or resource group",
                        e);
            } else {
                throw e;
            }
        }
    }

    /**
     * Returns the list of tenants to which a user has access.
     * @param tenants the list of all known tenants
     * @param credentials the credentials of user whose tenants to list
     * @return the list of tenants the given user has access to
     */
    // TODO jonmv: Remove
    public List<Tenant> accessibleTenants(List<Tenant> tenants, Credentials credentials) {
        AthenzIdentity identity =  ((AthenzPrincipal) credentials.user()).getIdentity();
        return tenants.stream()
                      .filter(tenant ->    tenant.type() == Tenant.Type.athenz
                                        && userDomains.apply(identity).contains(((AthenzTenant) tenant).domain()))
                      .toList();
    }

    public void addTenantAdmin(AthenzDomain tenantDomain, AthenzUser user) {
        zmsClient.addRoleMember(new AthenzRole(tenantDomain, "tenancy." + service.getFullName() + ".admin"), user, Optional.empty());
    }

    private void deleteApplication(AthenzDomain domain, ApplicationName application, OAuthCredentials oAuthCredentials) {
        log("deleteProviderResourceGroup(tenantDomain=%s, providerDomain=%s, service=%s, resourceGroup=%s)",
            domain, service.getDomain().getName(), service.getName(), application);
        zmsClient.deleteProviderResourceGroup(domain, service, application.value(), oAuthCredentials);
    }

    public boolean hasApplicationAccess(
            AthenzIdentity identity, ApplicationAction action, AthenzDomain tenantDomain, ApplicationName applicationName, Optional<ZoneId> zone) {
        return hasAccess(
                action.name(), applicationResourceString(tenantDomain, applicationName, zone), identity);
    }

    public boolean hasTenantAdminAccess(AthenzIdentity identity, AthenzDomain tenantDomain) {
        return hasAccess(TenantAction._modify_.name(), tenantResourceString(tenantDomain), identity);
    }

    public boolean hasHostedOperatorAccess(AthenzIdentity identity) {
        return hasAccess("modify", service.getDomain().getName() + ":hosted-vespa", identity);
    }

    public boolean hasHostedSupporterAccess(AthenzIdentity identity) {
        return hasAccess("read", service.getDomain().getName() + ":hosted-vespa", identity);
    }

    public boolean canLaunch(AthenzIdentity principal, AthenzService service) {
        return hasAccess("launch", service.getDomain().getName() + ":service."+service.getName(), principal);
    }

    public boolean hasSystemFlagsAccess(AthenzIdentity identity, boolean dryRun) {
        return hasAccess(dryRun ? "dryrun" : "deploy", new AthenzResourceName(service.getDomain(), "system-flags").toResourceNameString(), identity);
    }

    public boolean hasPaymentCallbackAccess(AthenzIdentity identity) {
        return hasAccess("callback", new AthenzResourceName(service.getDomain().getName(), "payment-notification-resource").toResourceNameString(), identity);
    }

    public boolean hasAccountingAccess(AthenzIdentity identity) {
        return hasAccess("modify", new AthenzResourceName(service.getDomain().getName(), "hosted-accounting-resource").toResourceNameString(), identity);
    }

    /**
     * Used when creating tenancies. As there are no tenancy policies at this point,
     * we cannot use {@link #hasTenantAdminAccess(AthenzIdentity, AthenzDomain)}
     */
    private void verifyIsDomainAdmin(AthenzIdentity identity, AthenzDomain domain) {
        log("getMembership(domain=%s, role=%s, principal=%s)", domain, "admin", identity);
        if ( ! zmsClient.getMembership(new AthenzRole(domain, "admin"), identity))
            throw new RestApiException.Forbidden(
                    Text.format("The user '%s' is not admin in Athenz domain '%s'", identity.getFullName(), domain.getName()));
    }

    public List<AthenzDomain> getDomainList(String prefix) {
        log.log(Level.FINE, "getDomainList(prefix=%s)", prefix);
        return zmsClient.getDomainList(prefix);
    }

    private static Set<RoleAction> createTenantRoleActions() {
        return Arrays.stream(ApplicationAction.values())
                .map(action -> new RoleAction(action.roleName, action.name()))
                .collect(Collectors.toSet());
    }

    private boolean hasAccess(String action, String resource, AthenzIdentity identity) {
        return accessRights.test(new AccessTuple(resource, action, identity));
    }

    private boolean lookupAccess(AccessTuple t) {
        boolean result = ztsClient.hasAccess(AthenzResourceName.fromString(t.resource), t.action, t.identity);
        log("getAccess(action=%s, resource=%s, principal=%s) = %b", t.action, t.resource, t.identity, result);
        return result;
    }

    private static void log(String format, Object... args) {
        log.log(Level.FINE, String.format(format, args));
    }

    private String resourceStringPrefix(AthenzDomain tenantDomain) {
        return Text.format("%s:service.%s.tenant.%s",
                             service.getDomain().getName(), service.getName(), tenantDomain.getName());
    }

    private String tenantResourceString(AthenzDomain tenantDomain) {
        return resourceStringPrefix(tenantDomain) + ".wildcard";
    }

    private String applicationResourceString(AthenzDomain tenantDomain, ApplicationName applicationName, Optional<ZoneId> zone) {
        // If environment is not provided, add .wildcard to match .* in the policy resource (* is not allowed in the request)
        String environment = zone.map(ZoneId::environment).map(Environment::value).orElse("wildcard");
        return resourceStringPrefix(tenantDomain) + "." + "res_group" + "." + applicationName.value() + "." + environment;
    }

    private enum TenantAction {
        // This is meant to match only the '*' action of the 'admin' role.
        // If needed, we can replace it with 'create', 'delete' etc. later.
        _modify_
    }


    private static class AccessTuple {

        private final String resource;
        private final String action;
        private final AthenzIdentity identity;

        private AccessTuple(String resource, String action, AthenzIdentity identity) {
            this.resource = resource;
            this.action = action;
            this.identity = identity;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AccessTuple that = (AccessTuple) o;
            return resource.equals(that.resource) &&
                   action.equals(that.action) &&
                   identity.equals(that.identity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resource, action, identity);
        }

    }

}
