// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.impl.ZmsClientFacade;
import com.yahoo.vespa.hosted.controller.concurrent.Once;
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
    private final ZmsClientFacade zmsClient;
    private final ZtsClient ztsClient;
    private final AthenzService controllerIdentity;

    public TenantController(Controller controller, CuratorDb curator, AthenzClientFactory athenzClientFactory) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.curator = Objects.requireNonNull(curator, "curator must be non-null");
        this.controllerIdentity = athenzClientFactory.getControllerIdentity();
        this.zmsClient = new ZmsClientFacade(athenzClientFactory.createZmsClient(), controllerIdentity);
        this.ztsClient = athenzClientFactory.createZtsClient();

        // Update serialization format of all tenants
        Once.after(Duration.ofMinutes(1), () -> {
            Instant start = controller.clock().instant();
            int count = 0;
            for (TenantName name : curator.readTenantNames()) {
                try (Lock lock = lock(name)) {
                    // Get while holding lock so that we know we're operating on a current version
                    Optional<Tenant> optionalTenant = get(name);
                    if (!optionalTenant.isPresent()) continue; // Deleted while updating, skip

                    Tenant tenant = optionalTenant.get();
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
            log.log(Level.INFO, String.format("Wrote %d tenants in %s", count,
                                              Duration.between(start, controller.clock().instant())));
        });
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
            Set<AthenzDomain> userDomains = new HashSet<>(ztsClient.getTenantDomains(controllerIdentity, athenzUser, "admin"));
            return asList().stream()
                           .filter(tenant -> isUser(tenant, user) ||
                                             userDomains.stream().anyMatch(domain -> inDomain(tenant, domain)))
                           .collect(Collectors.toList());
    }

    /** Locks a tenant for modification and applies the given action. */
    public <T extends LockedTenant> void lockIfPresent(TenantName name, Class<T> token, Consumer<T> action) {
        try (Lock lock = lock(name)) {
            get(name).map(tenant -> LockedTenant.of(tenant, lock))
                     .map(token::cast)
                     .ifPresent(action);
        }
    }

    /** Lock a tenant for modification and apply action. Throws if the tenant does not exist */
    public <T extends LockedTenant> void lockOrThrow(TenantName name, Class<T> token, Consumer<T> action) {
        try (Lock lock = lock(name)) {
            action.accept(token.cast(LockedTenant.of(require(name), lock)));
        }
    }

    public Tenant require(TenantName name) {
        return get(name).orElseThrow(() -> new IllegalArgumentException("No such tenant '" + name + "'."));
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
    public void create(AthenzTenant tenant, OktaAccessToken token) {
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
            zmsClient.createTenant(domain, token);
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
    public Optional<Tenant> get(TenantName name) {
        return curator.readTenant(name);
    }

    /** Find tenant by name */
    public Optional<Tenant> get(String name) {
        return get(TenantName.from(name));
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
    public LockedTenant.Athenz withDomain(LockedTenant.Athenz tenant, AthenzDomain newDomain, OktaAccessToken token) {
        AthenzTenant athenzTenant = tenant.get();
        AthenzDomain existingDomain = athenzTenant.domain();
        if (existingDomain.equals(newDomain)) return tenant;
        Optional<Tenant> existingTenantWithNewDomain = tenantIn(newDomain);
        if (existingTenantWithNewDomain.isPresent())
            throw new IllegalArgumentException("Could not set domain of " + tenant + " to '" + newDomain +
                                               "':" + existingTenantWithNewDomain.get() + " already has this domain");

        zmsClient.createTenant(newDomain, token);
        List<Application> applications = controller.applications().asList(tenant.get().name());
        applications.forEach(a -> zmsClient.addApplication(newDomain, new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(a.id().application().value()), token));
        applications.forEach(a -> zmsClient.deleteApplication(existingDomain, new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(a.id().application().value()), token));
        zmsClient.deleteTenant(existingDomain, token);
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
    public void deleteTenant(AthenzTenant tenant, OktaAccessToken token) {
        try (Lock lock = lock(tenant.name())) {
            deleteTenant(tenant.name());
            zmsClient.deleteTenant(tenant.domain(), token);
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
        if (get(name).isPresent() ||
            // Underscores are allowed in existing Athenz tenant names, but tenants with - and _ cannot co-exist. E.g.
            // my-tenant cannot be created if my_tenant exists.
            get(dashToUnderscore(name.value())).isPresent()) {
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
