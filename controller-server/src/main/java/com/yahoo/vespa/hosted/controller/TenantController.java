// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsClient;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.vespa.hosted.controller.persistence.ControllerDb;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.PersistenceException;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /** The controller owning this */    
    private final Controller controller;

    /** For permanent storage */
    private final ControllerDb db;

    /** For working memory storage and sharing between controllers */
    private final CuratorDb curator;

    private final AthenzClientFactory athenzClientFactory;

    public TenantController(Controller controller, ControllerDb db, CuratorDb curator,
                            AthenzClientFactory athenzClientFactory) {
        this.controller = controller;
        this.db = db;
        this.curator = curator;
        this.athenzClientFactory = athenzClientFactory;
        // Write all tenants to ensure persisted data uses latest serialization format
        for (Tenant tenant : db.listTenants()) {
            try (Lock lock = lock(tenant.name())) {
                if (tenant instanceof AthenzTenant) {
                    curator.writeTenant((AthenzTenant) tenant);
                } else if (tenant instanceof UserTenant) {
                    curator.writeTenant((UserTenant) tenant);
                } else {
                    throw new IllegalArgumentException("Unknown tenant type: " + tenant.getClass().getSimpleName());
                }
            }
        }
    }

    /** Returns a list of all known tenants sorted by name */
    public List<Tenant> asList() {
        return db.listTenants().stream()
                 .sorted(Comparator.comparing(Tenant::name))
                 .collect(Collectors.toList());
    }

    /** Returns a list of all tenants accessible by the given user */
    public List<Tenant> asList(UserId user) {
        AthenzUser athenzUser = AthenzUser.fromUserId(user.id());
        Set<AthenzDomain> userDomains = new HashSet<>(athenzClientFactory.createZtsClientWithServicePrincipal()
                                                                         .getTenantDomainsForUser(athenzUser));
        return asList().stream()
                       .filter(tenant -> tenant instanceof UserTenant||
                                         userDomains.stream().anyMatch(domain -> inDomain(tenant, domain)))
                       .collect(Collectors.toList());
    }

    /** Create an user tenant with given username */
    public void create(UserTenant tenant) {
        try (Lock lock = lock(tenant.name())) {
            requireNonExistent(tenant.name());
            db.createTenant(tenant);
            log.info("Created " + tenant);
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
            db.createTenant(tenant);
            log.info("Created " + tenant);
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
        try {
            return db.getTenant(name);
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    /** Find tenant by name */
    public Optional<Tenant> tenant(String name) {
        return tenant(TenantName.from(name));
    }

    /** Find Athenz tenant by name */
    public Optional<AthenzTenant> athenzTenant(TenantName name) {
        try {
            return db.getAthenzTenant(name);
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    /** Update Athenz tenant */
    public void updateTenant(AthenzTenant updatedTenant, NToken token) {
        try (Lock lock = lock(updatedTenant.name())) {
            requireExists(updatedTenant.name());
            updateAthenzDomain(updatedTenant, token);
            db.updateTenant(updatedTenant);
            log.info("Updated " + updatedTenant);
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
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
        try {
            if ( ! controller.applications().asList(name).isEmpty()) {
                throw new IllegalArgumentException("Could not delete tenant '" + name.value()
                                                   + "': This tenant has active applications");
            }
            db.deleteTenant(name);
            log.info("Deleted " + name);
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateAthenzDomain(AthenzTenant updatedTenant, NToken token) {
        Optional<AthenzTenant> existingTenant = athenzTenant(updatedTenant.name());
        if ( ! existingTenant.isPresent()) return;

        AthenzDomain existingDomain = existingTenant.get().domain();
        AthenzDomain newDomain = updatedTenant.domain();
        if (existingDomain.equals(newDomain)) return;
        Optional<Tenant> existingTenantWithNewDomain = tenantIn(newDomain);
        if (existingTenantWithNewDomain.isPresent())
            throw new IllegalArgumentException("Could not set domain of " + updatedTenant + " to '" + newDomain +
                                               "':" + existingTenantWithNewDomain.get() + " already has this domain");

        ZmsClient zmsClient = athenzClientFactory.createZmsClientWithAuthorizedServiceToken(token);
        zmsClient.createTenant(newDomain);
        List<Application> applications = controller.applications().asList(existingTenant.get().name());
        applications.forEach(a -> zmsClient.addApplication(newDomain, new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(a.id().application().value())));
        applications.forEach(a -> zmsClient.deleteApplication(existingDomain, new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(a.id().application().value())));
        zmsClient.deleteTenant(existingDomain);
        log.info("Updated Athens domain for " + updatedTenant + " from " + existingDomain + " to " + newDomain);
    }

    private void requireNonExistent(TenantName name) {
        if (tenant(name).isPresent() ||
            // Underscores are allowed in existing Athenz tenant names, but tenants with - and _ cannot co-exist. E.g.
            // my-tenant cannot be created if my_tenant exists.
            tenant(dashToUnderscore(name.value())).isPresent()) {
            throw new IllegalArgumentException("Tenant '" + name + "' already exists");
        }
    }

    private void requireExists(TenantName name) {
        if (!tenant(name).isPresent()) {
            throw new IllegalArgumentException("Tenant '" + name + "' does not exist");
        }
    }

    /**
     * Returns a lock which provides exclusive rights to changing this tenant.
     * Any operation which stores a tenant need to first acquire this lock, then read, modify
     * and store the tenant, and finally release (close) the lock.
     */
    private Lock lock(TenantName tenant) {
        return curator.lock(tenant, Duration.ofMinutes(10));
    }

    private static boolean inDomain(Tenant tenant, AthenzDomain domain) {
        return tenant instanceof AthenzTenant && ((AthenzTenant) tenant).in(domain);
    }

    private static String dashToUnderscore(String s) {
        return s.replace('-', '_');
    }

}
