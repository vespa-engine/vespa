package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

/** A role which requires the context of a system, a tenant, and an application. */
public interface RoleInSystemWithTenantAndApplication {
    RoleMembership limitedTo(ApplicationName application, TenantName tenant, SystemName system);
}
