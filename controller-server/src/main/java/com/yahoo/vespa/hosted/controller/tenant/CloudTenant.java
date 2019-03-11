package com.yahoo.vespa.hosted.controller.tenant;

import com.yahoo.config.provision.TenantName;

import java.util.Optional;

/**
 * A tenant as vague as its name.
 *
 * Only a reference to a cloud identity provider, and some billing info, is known for this tenant type.
 *
 * @author jonmv
 */
public class CloudTenant extends Tenant {

    private final BillingInfo billingInfo;

    /** Public for the serialization layer — do not use! */
    public CloudTenant(TenantName name, BillingInfo info) {
        super(name, Optional.empty());
        billingInfo = info;
    }

    /** Creates a tenant with the given name, provided it passes validation. */
    public static CloudTenant create(TenantName tenantName, BillingInfo billingInfo) {
        return new CloudTenant(requireName(tenantName), billingInfo);
    }

    /** Returns the billing info for this tenant. */
    public BillingInfo billingInfo() { return billingInfo; }

}
