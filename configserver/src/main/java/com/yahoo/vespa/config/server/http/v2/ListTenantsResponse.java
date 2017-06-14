// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;


import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.SessionResponse;

import java.util.Collection;

/**
 * Tenant list response
 * 
 * @author vegardh
 *
 */
public class ListTenantsResponse extends SessionResponse {
    private final Collection<TenantName> tenantNames;
    
    public ListTenantsResponse(final Collection<TenantName> tenants) {
        super();
        this.tenantNames = tenants;
        Cursor tenantArray = this.root.setArray("tenants");
        synchronized (tenants) {
            for (final TenantName tenantName : tenants) {
                tenantArray.addString(tenantName.value());
            }
        }
    }

    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }

    public Collection<TenantName> getTenantNames() {
        return tenantNames;
    }
}
