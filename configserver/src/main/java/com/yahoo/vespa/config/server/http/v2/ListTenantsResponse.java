// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.TenantName;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;

/**
 * Tenant list response
 * 
 * @author vegardh
 */
public class ListTenantsResponse extends SlimeJsonResponse {

    ListTenantsResponse(ImmutableSet<TenantName> tenants) {
        Cursor tenantArray = slime.setObject().setArray("tenants");
        tenants.forEach(tenantName -> tenantArray.addString(tenantName.value()));
    }

}
