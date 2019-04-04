package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

/**
 * A {@link Role} with a {@link Context} of a {@link SystemName} and a {@link TenantName}.
 *
 * @author jonmv
 */
public class TenantRole extends Role {

    TenantRole(ProtoRole protoRole, SystemName system, TenantName tenant) {
        super(protoRole, Context.limitedTo(tenant, system));
    }

    /** Returns the {@link TenantName} this is bound to. */
    public TenantName tenant() { return context.tenant().get(); }

    @Override
    public String toString() {
        return "role '" + proto() + "' of '" + tenant() + "'";
    }

}
