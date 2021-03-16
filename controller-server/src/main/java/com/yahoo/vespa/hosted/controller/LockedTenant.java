// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.TenantName;
import com.yahoo.security.KeyUtils;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfo;

import java.security.Principal;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * A tenant that has been locked for modification. Provides methods for modifying a tenant's fields.
 *
 * @author mpolden
 * @author jonmv
 */
public abstract class LockedTenant {

    final TenantName name;
    final Instant createdAt;
    final LastLoginInfo lastLoginInfo;

    private LockedTenant(TenantName name, Instant createdAt, LastLoginInfo lastLoginInfo) {
        this.name = requireNonNull(name);
        this.createdAt = requireNonNull(createdAt);
        this.lastLoginInfo = requireNonNull(lastLoginInfo);
    }

    static LockedTenant of(Tenant tenant, Lock lock) {
        switch (tenant.type()) {
            case athenz: return new Athenz((AthenzTenant) tenant);
            case cloud:  return new Cloud((CloudTenant) tenant);
            default:     throw new IllegalArgumentException("Unexpected tenant type '" + tenant.getClass().getName() + "'.");
        }
    }

    /** Returns a read-only copy of this */
    public abstract Tenant get();

    public abstract LockedTenant with(LastLoginInfo lastLoginInfo);

    @Override
    public String toString() {
        return "tenant '" + name + "'";
    }


    /** A locked AthenzTenant. */
    public static class Athenz extends LockedTenant {

        private final AthenzDomain domain;
        private final Property property;
        private final Optional<PropertyId> propertyId;
        private final Optional<Contact> contact;

        private Athenz(TenantName name, AthenzDomain domain, Property property, Optional<PropertyId> propertyId,
                       Optional<Contact> contact, Instant createdAt, LastLoginInfo lastLoginInfo) {
            super(name, createdAt, lastLoginInfo);
            this.domain = domain;
            this.property = property;
            this.propertyId = propertyId;
            this.contact = contact;
        }

        private Athenz(AthenzTenant tenant) {
            this(tenant.name(), tenant.domain(), tenant.property(), tenant.propertyId(), tenant.contact(), tenant.createdAt(), tenant.lastLoginInfo());
        }

        @Override
        public AthenzTenant get() {
            return new AthenzTenant(name, domain, property, propertyId, contact, createdAt, lastLoginInfo);
        }

        public Athenz with(AthenzDomain domain) {
            return new Athenz(name, domain, property, propertyId, contact, createdAt, lastLoginInfo);
        }

        public Athenz with(Property property) {
            return new Athenz(name, domain, property, propertyId, contact, createdAt, lastLoginInfo);
        }

        public Athenz with(PropertyId propertyId) {
            return new Athenz(name, domain, property, Optional.of(propertyId), contact, createdAt, lastLoginInfo);
        }

        public Athenz with(Contact contact) {
            return new Athenz(name, domain, property, propertyId, Optional.of(contact), createdAt, lastLoginInfo);
        }

        @Override
        public LockedTenant with(LastLoginInfo lastLoginInfo) {
            return new Athenz(name, domain, property, propertyId, contact, createdAt, lastLoginInfo);
        }

    }


    /** A locked CloudTenant. */
    public static class Cloud extends LockedTenant {

        private final Optional<Principal> creator;
        private final BiMap<PublicKey, Principal> developerKeys;
        private final TenantInfo info;
        private final List<TenantSecretStore> tenantSecretStores;
        private final Optional<String> archiveAccessRole;

        private Cloud(TenantName name, Instant createdAt, LastLoginInfo lastLoginInfo, Optional<Principal> creator,
                      BiMap<PublicKey, Principal> developerKeys, TenantInfo info,
                      List<TenantSecretStore> tenantSecretStores, Optional<String> archiveAccessRole) {
            super(name, createdAt, lastLoginInfo);
            this.developerKeys = ImmutableBiMap.copyOf(developerKeys);
            this.creator = creator;
            this.info = info;
            this.tenantSecretStores = tenantSecretStores;
            this.archiveAccessRole = archiveAccessRole;
        }

        private Cloud(CloudTenant tenant) {
            this(tenant.name(), tenant.createdAt(), tenant.lastLoginInfo(), Optional.empty(), tenant.developerKeys(), tenant.info(), tenant.tenantSecretStores(), tenant.archiveAccessRole());
        }

        @Override
        public CloudTenant get() {
            return new CloudTenant(name, createdAt, lastLoginInfo, creator, developerKeys, info, tenantSecretStores, archiveAccessRole);
        }

        public Cloud withDeveloperKey(PublicKey key, Principal principal) {
            BiMap<PublicKey, Principal> keys = HashBiMap.create(developerKeys);
            if (keys.containsKey(key))
                throw new IllegalArgumentException("Key " + KeyUtils.toPem(key) + " is already owned by " + keys.get(key));
            keys.put(key, principal);
            return new Cloud(name, createdAt, lastLoginInfo, creator, keys, info, tenantSecretStores, archiveAccessRole);
        }

        public Cloud withoutDeveloperKey(PublicKey key) {
            BiMap<PublicKey, Principal> keys = HashBiMap.create(developerKeys);
            keys.remove(key);
            return new Cloud(name, createdAt, lastLoginInfo, creator, keys, info, tenantSecretStores, archiveAccessRole);
        }

        public Cloud withInfo(TenantInfo newInfo) {
            return new Cloud(name, createdAt, lastLoginInfo, creator, developerKeys, newInfo, tenantSecretStores, archiveAccessRole);
        }

        @Override
        public LockedTenant with(LastLoginInfo lastLoginInfo) {
            return new Cloud(name, createdAt, lastLoginInfo, creator, developerKeys, info, tenantSecretStores, archiveAccessRole);
        }

        public Cloud withSecretStore(TenantSecretStore tenantSecretStore) {
            ArrayList<TenantSecretStore> secretStores = new ArrayList<>(tenantSecretStores);
            secretStores.add(tenantSecretStore);
            return new Cloud(name, createdAt, lastLoginInfo, creator, developerKeys, info, secretStores, archiveAccessRole);
        }

        public Cloud withoutSecretStore(TenantSecretStore tenantSecretStore) {
            ArrayList<TenantSecretStore> secretStores = new ArrayList<>(tenantSecretStores);
            secretStores.remove(tenantSecretStore);
            return new Cloud(name, createdAt, lastLoginInfo, creator, developerKeys, info, secretStores, archiveAccessRole);
        }

        public Cloud withArchiveAccessRole(Optional<String> role) {
            return new Cloud(name, createdAt, lastLoginInfo, creator, developerKeys, info, tenantSecretStores, role);
        }
    }

}
