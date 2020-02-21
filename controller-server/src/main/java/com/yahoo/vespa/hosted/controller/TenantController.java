// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.concurrent.Once;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.security.AccessControl;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.security.TenantSpec;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final AccessControl accessControl;

    public TenantController(Controller controller, CuratorDb curator, AccessControl accessControl) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.curator = Objects.requireNonNull(curator, "curator must be non-null");
        this.accessControl = accessControl;

        // Update serialization format of all tenants
        Once.after(Duration.ofMinutes(1), () -> {
            Instant start = controller.clock().instant();
            int count = 0;
            for (TenantName name : curator.readTenantNames()) {
                if (name.value().startsWith(Tenant.userPrefix))
                    curator.removeTenant(name);
                else {
                    lockIfPresent(name, LockedTenant.class, this::store);
                    count++;
                }
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

    // TODO jonmv: Remove.
    /** Returns the list of tenants accessible to the given user. */
    public List<Tenant> asList(Credentials credentials) {
        return ((AthenzFacade) accessControl).accessibleTenants(asList(), credentials);
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

    /** Returns the tenant with the given name, or throws. */
    public Tenant require(TenantName name) {
        return get(name).orElseThrow(() -> new IllegalArgumentException("No such tenant '" + name + "'."));
    }

    /** Replace and store any previous version of given tenant */
    public void store(LockedTenant tenant) {
        curator.writeTenant(tenant.get());
    }

    /** Create a tenant, provided the given credentials are valid. */
    public void create(TenantSpec tenantSpec, Credentials credentials) {
        try (Lock lock = lock(tenantSpec.tenant())) {
            requireNonExistent(tenantSpec.tenant());
            curator.writeTenant(accessControl.createTenant(tenantSpec, credentials, asList()));
        }
    }

    /** Find tenant by name */
    public Optional<Tenant> get(TenantName name) {
        return curator.readTenant(name);
    }

    /** Find tenant by name */
    public Optional<Tenant> get(String name) {
        return get(TenantName.from(name));
    }

    /** Updates the tenant contained in the given tenant spec with new data. */
    public void update(TenantSpec tenantSpec, Credentials credentials) {
        try (Lock lock = lock(tenantSpec.tenant())) {
            curator.writeTenant(accessControl.updateTenant(tenantSpec, credentials, asList(),
                                                           controller.applications().asList(tenantSpec.tenant())));
        }
    }

    /** Deletes the given tenant. */
    public void delete(TenantName tenant, Credentials credentials) {
        try (Lock lock = lock(tenant)) {
            require(tenant);
            if ( ! controller.applications().asList(tenant).isEmpty())
                throw new IllegalArgumentException("Could not delete tenant '" + tenant.value()
                                                   + "': This tenant has active applications");

            curator.removeTenant(tenant);
            accessControl.deleteTenant(tenant, credentials);
        }
    }

    private void requireNonExistent(TenantName name) {
        if (   "hosted-vespa".equals(name.value())
            || get(name).isPresent()
            // Underscores are allowed in existing tenant names, but tenants with - and _ cannot co-exist. E.g.
            // my-tenant cannot be created if my_tenant exists.
            || get(name.value().replace('-', '_')).isPresent()) {
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

}
