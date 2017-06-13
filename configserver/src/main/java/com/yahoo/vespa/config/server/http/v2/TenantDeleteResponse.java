// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.SessionResponse;

/**
 * Response for tenant delete
 * 
 * @author vegardh
 *
 */
public class TenantDeleteResponse extends SessionResponse {

    public TenantDeleteResponse(TenantName tenant) {
        super();
        this.root.setString("message", "Tenant "+tenant+" deleted.");
    }
    
    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }
    
}
