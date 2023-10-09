// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.config.provision.TenantName;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;

import java.util.Set;

/**
 * Tenant list response
 * 
 * @author vegardh
 */
public class ListTenantsResponse extends SlimeJsonResponse {

    public ListTenantsResponse(Set<TenantName> tenants) {
        Cursor tenantArray = slime.setObject().setArray("tenants");
        tenants.forEach(tenantName -> tenantArray.addString(tenantName.value()));
    }

}
