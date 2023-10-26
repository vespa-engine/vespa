// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.request;

import com.yahoo.config.provision.ApplicationId;

/**
 * Config REST requests that have been bound to an application id
 * 
 * @author vegardh
 */
public interface TenantRequest {

    ApplicationId getApplicationId();
    
}
