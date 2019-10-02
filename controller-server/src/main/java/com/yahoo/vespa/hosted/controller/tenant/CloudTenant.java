package com.yahoo.vespa.hosted.controller.tenant;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

/**
 * A paying tenant in a Vespa cloud service.
 *
 * @author jonmv
 */
public class CloudTenant extends Tenant {

    private final BillingInfo billingInfo;
    private final BiMap<String, Principal> pemDeveloperKeys;

    /** Public for the serialization layer — do not use! */
    public CloudTenant(TenantName name, BillingInfo info, BiMap<String, Principal> pemDeveloperKeys) {
        super(name, Optional.empty());
        billingInfo = info;
        this.pemDeveloperKeys = pemDeveloperKeys;
    }

    /** Creates a tenant with the given name, provided it passes validation. */
    public static CloudTenant create(TenantName tenantName, BillingInfo billingInfo) {
        return new CloudTenant(requireName(tenantName),
                               Objects.requireNonNull(billingInfo),
                               ImmutableBiMap.of());
    }

    /** Returns the billing info for this tenant. */
    public BillingInfo billingInfo() { return billingInfo; }

    /** Returns the set of developer keys and their corresponding developers for this tenant. */
    public BiMap<String, Principal> pemDeveloperKeys() { return pemDeveloperKeys; }

    @Override
    public Type type() {
        return Type.cloud;
    }

}
