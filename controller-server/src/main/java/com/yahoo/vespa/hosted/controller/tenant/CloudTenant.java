// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.TenantName;

import java.security.Principal;
import java.security.PublicKey;
import java.util.Objects;
import java.util.Optional;

/**
 * A paying tenant in a Vespa cloud service.
 *
 * @author jonmv
 */
public class CloudTenant extends Tenant {

    private final Optional<Principal> creator;
    private final BiMap<PublicKey, Principal> developerKeys;
    private final TenantInfo info;

    /** Public for the serialization layer — do not use! */
    public CloudTenant(TenantName name, Optional<Principal> creator, BiMap<PublicKey, Principal> developerKeys, TenantInfo info) {
        super(name, Optional.empty());
        this.creator = creator;
        this.developerKeys = developerKeys;
        this.info = Objects.requireNonNull(info);
    }

    /** Creates a tenant with the given name, provided it passes validation. */
    public static CloudTenant create(TenantName tenantName, Principal creator) {
        return new CloudTenant(requireName(tenantName),
                               Optional.ofNullable(creator),
                               ImmutableBiMap.of(), TenantInfo.EmptyInfo);
    }

    /** The user that created the tenant */
    public Optional<Principal> creator() {
        return creator;
    }

    /** Legal name, addresses etc */
    public TenantInfo info() {
        return info;
    }

    /** Returns the set of developer keys and their corresponding developers for this tenant. */
    public BiMap<PublicKey, Principal> developerKeys() { return developerKeys; }

    @Override
    public Type type() {
        return Type.cloud;
    }

}
