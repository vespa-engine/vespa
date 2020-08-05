// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.TenantName;

import java.security.Principal;
import java.security.PublicKey;
import java.util.Optional;

/**
 * A paying tenant in a Vespa cloud service.
 *
 * @author jonmv
 */
public class CloudTenant extends Tenant {

    private final BiMap<PublicKey, Principal> developerKeys;

    /** Public for the serialization layer — do not use! */
    public CloudTenant(TenantName name, BiMap<PublicKey, Principal> developerKeys) {
        super(name, Optional.empty());
        this.developerKeys = developerKeys;
    }

    /** Creates a tenant with the given name, provided it passes validation. */
    public static CloudTenant create(TenantName tenantName) {
        return new CloudTenant(requireName(tenantName),
                               ImmutableBiMap.of());
    }

    /** Returns the set of developer keys and their corresponding developers for this tenant. */
    public BiMap<PublicKey, Principal> developerKeys() { return developerKeys; }

    @Override
    public Type type() {
        return Type.cloud;
    }

}
