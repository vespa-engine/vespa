// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;

/**
 * Extracts access control data for {@link CloudTenant}s from HTTP requests.
 *
 * @author jonmv
 */
public class CloudAccessControlRequests implements AccessControlRequests {

    @Override
    public CloudTenantSpec specification(TenantName tenant, Inspector requestObject) {
        // TODO extract marketplace token.
        return new CloudTenantSpec(tenant, "token");
    }

    @Override
    public Credentials credentials(TenantName tenant, Inspector requestObject, HttpRequest request) {
        // TODO Include roles, if this is to be used for displaying accessible data.
        return new Credentials(request.getUserPrincipal());
    }

}
