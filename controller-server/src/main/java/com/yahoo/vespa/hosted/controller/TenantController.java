// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.TenantName;
import com.yahoo.text.Text;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.concurrent.Once;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.security.AccessControl;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.security.TenantSpec;
import com.yahoo.vespa.hosted.controller.tenant.DeletedTenant;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
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
        this.accessControl = Objects.requireNonNull(accessControl, "accessControl must be non-null");

        // Update serialization format of all tenants
        Once.after(Duration.ofMinutes(1), () -> {
            Instant start = controller.clock().instant();
            int count = 0;
            for (TenantName name : curator.readTenantNames()) {
                lockIfPresent(name, LockedTenant.class, this::store);
                count++;
            }
            log.log(Level.INFO, Text.format("Wrote %d tenants in %s", count,
                                              Duration.between(start, controller.clock().instant())));
        });
    }

    /** Returns a list of all known, non-deleted tenants sorted by name */
    public List<Tenant> asList() {
        return asList(false);
    }

    /** Returns a list of all known tenants sorted by name */
    public List<Tenant> asList(boolean includeDeleted) {
        return curator.readTenants().stream()
                .filter(tenant -> tenant.type() != Tenant.Type.deleted || includeDeleted)
                .sorted(Comparator.comparing(Tenant::name))
                .collect(Collectors.toList());
    }

    /** Locks a tenant for modification and applies the given action. */
    public <T extends LockedTenant> void lockIfPresent(TenantName name, Class<T> token, Consumer<T> action) {
        try (Mutex lock = lock(name)) {
            get(name).map(tenant -> LockedTenant.of(tenant, lock))
                     .map(token::cast)
                     .ifPresent(action);
        }
    }

    /** Lock a tenant for modification and apply action. Throws if the tenant does not exist */
    public <T extends LockedTenant> void lockOrThrow(TenantName name, Class<T> token, Consumer<T> action) {
        try (Mutex lock = lock(name)) {
            action.accept(token.cast(LockedTenant.of(require(name), lock)));
        }
    }

    /** Returns the tenant with the given name, or throws. */
    public Tenant require(TenantName name) {
        return get(name).orElseThrow(() -> new IllegalArgumentException("No such tenant '" + name + "'."));
    }

    /** Returns the tenant with the given name, and ensures the type */
    public <T extends Tenant> T require(TenantName name, Class<T> tenantType) {
        return get(name)
                .map(t -> {
                    try { return tenantType.cast(t); } catch (ClassCastException e) {
                        throw new IllegalArgumentException("Tenant '" + name + "' was of type '" + t.getClass().getSimpleName() + "' and not '" + tenantType.getSimpleName() + "'");
                    }
                })
                .orElseThrow(() -> new IllegalArgumentException("No such tenant '" + name + "'."));
    }

    /** Replace and store any previous version of given tenant */
    public void store(LockedTenant tenant) {
        curator.writeTenant(tenant.get());
    }

    /** Create a tenant, provided the given credentials are valid. */
    public void create(TenantSpec tenantSpec, Credentials credentials) {
        try (Mutex lock = lock(tenantSpec.tenant())) {
            TenantId.validate(tenantSpec.tenant().value());
            requireNonExistent(tenantSpec.tenant());
            curator.writeTenant(accessControl.createTenant(tenantSpec, controller.clock().instant(), credentials, asList()));

            // We should create tenant roles here but it takes too long - assuming the TenantRoleMaintainer will do it Soon™
        }
    }

    /** Find tenant by name */
    public Optional<Tenant> get(TenantName name) {
        return get(name, false);
    }

    public Optional<Tenant> get(TenantName name, boolean includeDeleted) {
        return curator.readTenant(name)
                .filter(tenant -> tenant.type() != Tenant.Type.deleted || includeDeleted);
    }

    /** Find tenant by name */
    public Optional<Tenant> get(String name) {
        return get(TenantName.from(name));
    }

    /** Updates the tenant contained in the given tenant spec with new data. */
    public void update(TenantSpec tenantSpec, Credentials credentials) {
        try (Mutex lock = lock(tenantSpec.tenant())) {
            curator.writeTenant(accessControl.updateTenant(tenantSpec, credentials, asList(),
                                                           controller.applications().asList(tenantSpec.tenant())));
        }
    }

    /**
     * Update last login times for the given tenant at the given user levers with the given instant, but only if the
     * new instant is later
     */
    public void updateLastLogin(TenantName tenantName, List<LastLoginInfo.UserLevel> userLevels, Instant loggedInAt) {
        try (Mutex lock = lock(tenantName)) {
            Tenant tenant = require(tenantName);
            LastLoginInfo loginInfo = tenant.lastLoginInfo();
            for (LastLoginInfo.UserLevel userLevel : userLevels)
                loginInfo = loginInfo.withLastLoginIfLater(userLevel, loggedInAt);

            if (tenant.lastLoginInfo().equals(loginInfo)) return; // no change
            curator.writeTenant(LockedTenant.of(tenant, lock).with(loginInfo).get());
        }
    }

    /** Deletes the given tenant. */
    public void delete(TenantName tenant, Optional<Credentials> credentials, boolean forget) {
        try (Mutex lock = lock(tenant)) {
            Tenant oldTenant = get(tenant, true)
                    .orElseThrow(() -> new NotExistsException("Could not delete tenant '" + tenant + "': Tenant not found"));

            if (oldTenant.type() != Tenant.Type.deleted) {
                if (!controller.applications().asList(tenant).isEmpty())
                    throw new IllegalArgumentException("Could not delete tenant '" + tenant.value()
                            + "': This tenant has active applications");

                if (oldTenant.type() == Tenant.Type.athenz) {
                    credentials.ifPresent(creds -> accessControl.deleteTenant(tenant, creds));
                } else if (oldTenant.type() == Tenant.Type.cloud) {
                    accessControl.deleteTenant(tenant, null);
                } else {
                    throw new IllegalArgumentException("Could not delete tenant '" + tenant.value()
                            + ": This tenant is of unhandled type " + oldTenant.type());
                }

                controller.notificationsDb().removeNotifications(NotificationSource.from(tenant));
            }

            if (forget) curator.removeTenant(tenant);
            else curator.writeTenant(new DeletedTenant(tenant, oldTenant.createdAt(), controller.clock().instant()));
        }
    }

    private void requireNonExistent(TenantName name) {
        var tenant = get(name, true);
        if (tenant.isPresent() && tenant.get().type().equals(Tenant.Type.deleted)) {
            throw new IllegalArgumentException("Tenant '" + name + "' cannot be created, try a different name");
        }
        if (SystemApplication.TENANT.equals(name)
            || tenant.isPresent()
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
    private Mutex lock(TenantName tenant) {
        return curator.lock(tenant);
    }

}
