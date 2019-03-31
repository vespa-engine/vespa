package com.yahoo.vespa.hosted.controller.role;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

/** A role which requires the context of a system and a tenant. */
public interface RoleInSystemWithTenant {
    RoleMembership limitedTo(TenantName tenant, SystemName system);
}
