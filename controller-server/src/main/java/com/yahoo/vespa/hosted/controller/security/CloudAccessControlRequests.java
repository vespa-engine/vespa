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
    public CloudTenantClaim getTenantClaim(TenantName tenant, Inspector requestObject) {
        // TODO extract marketplace token.
        return new CloudTenantClaim(tenant, "token");
    }

    @Override
    public Credentials getCredentials(TenantName tenant, Inspector requestObject, HttpRequest request) {
        // TODO Pick out JWT data and return a specialised credentials thing.
        return new Credentials(request.getUserPrincipal());
    }

}
