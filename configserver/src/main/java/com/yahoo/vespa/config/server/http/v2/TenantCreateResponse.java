// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.SessionResponse;

/**
 * Response for tenant create
 * 
 * @author vegardh
 *
 */
public class TenantCreateResponse extends SessionResponse {

    public TenantCreateResponse(TenantName tenant) {
        super();
        this.root.setString("message", "Tenant "+tenant+" created.");
    }
    
    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }
    
}
