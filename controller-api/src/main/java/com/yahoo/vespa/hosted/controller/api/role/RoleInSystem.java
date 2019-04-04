package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.SystemName;

/** A role which requires only the context of a system. */
public interface RoleInSystem {
    RoleMembership limitedTo(SystemName system);
}
