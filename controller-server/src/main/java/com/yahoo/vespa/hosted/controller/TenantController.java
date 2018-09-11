// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsClient;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A singleton owned by the Controller which contains the methods and state for controlling tenants.
 *
 * @author bratseth
 * @author mpolden
 */
public class TenantController {

    private static final Logger log = Logger.getLogger(TenantController.class.getName());

    private final Controller controller;
    private final CuratorDb curator;
    private final AthenzClientFactory athenzClientFactory;

    public TenantController(Controller controller, CuratorDb curator, AthenzClientFactory athenzClientFactory) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.curator = Objects.requireNonNull(curator, "curator must be non-null");
        this.athenzClientFactory = Objects.requireNonNull(athenzClientFactory, "athenzClientFactory must be non-null");

        // Write all tenants to ensure persisted data uses latest serialization format
        Instant start = controller.clock().instant();
        int count = 0;
        for (Tenant tenant : curator.readTenants()) {
            try (Lock lock = lock(tenant.name())) {
                if (tenant instanceof AthenzTenant) {
                    curator.writeTenant((AthenzTenant) tenant);
                } else if (tenant instanceof UserTenant) {
                    curator.writeTenant((UserTenant) tenant);
                } else {
                    throw new IllegalArgumentException("Unknown tenant type: " + tenant.getClass().getSimpleName());
                }
            }
            count++;
        }
        log.log(Level.INFO, String.format("Wrote %d tenants in %s", count, Duration.between(start, controller.clock().instant())));
    }

    /** Returns a list of all known tenants sorted by name */
    public List<Tenant> asList() {
        return curator.readTenants().stream()
                      .sorted(Comparator.comparing(Tenant::name))
                      .collect(Collectors.toList());
    }

    /** Returns a list of all tenants accessible by the given user */
    public List<Tenant> asList(UserId user) {
        AthenzUser athenzUser = AthenzUser.fromUserId(user.id());
        try (ZtsClient ztsClient = athenzClientFactory.createZtsClientWithServicePrincipal()) {
            Set<AthenzDomain> userDomains = new HashSet<>(ztsClient.getTenantDomains(athenzClientFactory.getControllerIdentity(), athenzUser, "admin"));
            return asList().stream()
                           .filter(tenant -> isUser(tenant, user) ||
                                             userDomains.stream().anyMatch(domain -> inDomain(tenant, domain)))
                           .collect(Collectors.toList());
        }
    }

    /**
     * Lock a tenant for modification and apply action. Only valid for Athenz tenants as it's the only type that
     * accepts modification.
     */
    public void lockIfPresent(TenantName name, Consumer<LockedTenant> action) {
        try (Lock lock = lock(name)) {
            athenzTenant(name).map(tenant -> new LockedTenant(tenant, lock)).ifPresent(action);
        }
    }

    /** Lock a tenant for modification and apply action. Throws if the tenant does not exist */
    public void lockOrThrow(TenantName name, Consumer<LockedTenant> action) {
        try (Lock lock = lock(name)) {
            action.accept(new LockedTenant(requireAthenzTenant(name), lock));
        }
    }

    /** Replace and store any previous version of given tenant */
    public void store(LockedTenant tenant) {
        curator.writeTenant(tenant.get());
    }

    /** Create an user tenant with given username */
    public void create(UserTenant tenant) {
        try (Lock lock = lock(tenant.name())) {
            requireNonExistent(tenant.name());
            curator.writeTenant(tenant);
        }
    }

    /** Create an Athenz tenant */
    public void create(AthenzTenant tenant, NToken token) {
        try (Lock lock = lock(tenant.name())) {
            requireNonExistent(tenant.name());
            AthenzDomain domain = tenant.domain();
            Optional<Tenant> existingTenantWithDomain = tenantIn(domain);
            if (existingTenantWithDomain.isPresent()) {
                throw new IllegalArgumentException("Could not create tenant '" + tenant.name().value() +
                                                   "': The Athens domain '" +
                                                   domain.getName() + "' is already connected to tenant '" +
                                                   existingTenantWithDomain.get().name().value() +
                                                   "'");
            }
            athenzClientFactory.createZmsClientWithAuthorizedServiceToken(token).createTenant(domain);
            curator.writeTenant(tenant);
        }
    }

    /** Returns the tenant in the given Athenz domain, or empty if none */
    private Optional<Tenant> tenantIn(AthenzDomain domain) {
        return asList().stream()
                       .filter(tenant -> inDomain(tenant, domain))
                       .findFirst();
    }

    /** Find tenant by name */
    public Optional<Tenant> tenant(TenantName name) {
        return curator.readTenant(name);
    }

    /** Find tenant by name */
    public Optional<Tenant> tenant(String name) {
        return tenant(TenantName.from(name));
    }

    /** Find Athenz tenant by name */
    public Optional<AthenzTenant> athenzTenant(TenantName name) {
        return curator.readAthenzTenant(name);
    }

    /** Returns Athenz tenant with name or throws if no such tenant exists */
    public AthenzTenant requireAthenzTenant(TenantName name) {
        return athenzTenant(name).orElseThrow(() -> new IllegalArgumentException("Tenant '" + name + "' not found"));
    }

    /** Update Athenz domain for tenant. Returns the updated tenant which must be explicitly stored */
    public LockedTenant withDomain(LockedTenant tenant, AthenzDomain newDomain, NToken token) {
        AthenzDomain existingDomain = tenant.get().domain();
        if (existingDomain.equals(newDomain)) return tenant;
        Optional<Tenant> existingTenantWithNewDomain = tenantIn(newDomain);
        if (existingTenantWithNewDomain.isPresent())
            throw new IllegalArgumentException("Could not set domain of " + tenant + " to '" + newDomain +
                                               "':" + existingTenantWithNewDomain.get() + " already has this domain");

        ZmsClient zmsClient = athenzClientFactory.createZmsClientWithAuthorizedServiceToken(token);
        zmsClient.createTenant(newDomain);
        List<Application> applications = controller.applications().asList(tenant.get().name());
        applications.forEach(a -> zmsClient.addApplication(newDomain, new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(a.id().application().value())));
        applications.forEach(a -> zmsClient.deleteApplication(existingDomain, new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(a.id().application().value())));
        zmsClient.deleteTenant(existingDomain);
        log.info("Set Athenz domain for '" + tenant + "' from '" + existingDomain + "' to '" + newDomain + "'");

        return tenant.with(newDomain);
    }

    /** Delete an user tenant */
    public void deleteTenant(UserTenant tenant) {
        try (Lock lock = lock(tenant.name())) {
            deleteTenant(tenant.name());
        }
    }

    /** Delete an Athenz tenant */
    public void deleteTenant(AthenzTenant tenant, NToken nToken) {
        try (Lock lock = lock(tenant.name())) {
            deleteTenant(tenant.name());
            athenzClientFactory.createZmsClientWithAuthorizedServiceToken(nToken).deleteTenant(tenant.domain());
        }
    }

    private void deleteTenant(TenantName name) {
        if (!controller.applications().asList(name).isEmpty()) {
            throw new IllegalArgumentException("Could not delete tenant '" + name.value()
                                               + "': This tenant has active applications");
        }
        curator.removeTenant(name);
    }

    private void requireNonExistent(TenantName name) {
        if (tenant(name).isPresent() ||
            // Underscores are allowed in existing Athenz tenant names, but tenants with - and _ cannot co-exist. E.g.
            // my-tenant cannot be created if my_tenant exists.
            tenant(dashToUnderscore(name.value())).isPresent()) {
            throw new IllegalArgumentException("Tenant '" + name + "' already exists");
        }
    }

    /**
     * Returns a lock which provides exclusive rights to changing this tenant.
     * Any operation which stores a tenant need to first acquire this lock, then read, modify
     * and store the tenant, and finally release (close) the lock.
     */
    private Lock lock(TenantName tenant) {
        return curator.lock(tenant);
    }

    private static boolean inDomain(Tenant tenant, AthenzDomain domain) {
        return tenant instanceof AthenzTenant && ((AthenzTenant) tenant).in(domain);
    }

    private static boolean isUser(Tenant tenant, UserId userId) {
        return tenant instanceof UserTenant && ((UserTenant) tenant).is(userId.id());
    }

    private static String dashToUnderscore(String s) {
        return s.replace('-', '_');
    }

}
