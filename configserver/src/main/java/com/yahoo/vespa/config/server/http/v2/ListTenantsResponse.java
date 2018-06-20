// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.SessionResponse;

/**
 * Tenant list response
 * 
 * @author vegardh
 *
 */
public class ListTenantsResponse extends SessionResponse {

    ListTenantsResponse(ImmutableSet<TenantName> tenants) {
        super();
        Cursor tenantArray = this.root.setArray("tenants");
        tenants.forEach(tenantName -> tenantArray.addString(tenantName.value()));
    }

    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }

}
