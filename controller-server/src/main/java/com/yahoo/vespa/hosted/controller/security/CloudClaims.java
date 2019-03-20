package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;

/**
 * Extracts permits for {@link CloudTenant}s from HTTP requests.
 *
 * @author jonmv
 */
public class CloudClaims implements Claims {

    @Override
    public CloudTenantClaim getTenantClaim(TenantName tenant, HttpRequest request) {
        return new CloudTenantClaim(tenant, request.getJDiscRequest().getUserPrincipal(), "token");
    }

    @Override
    public CloudApplicationClaim getApplicationClaim(ApplicationId application, HttpRequest request) {
        return new CloudApplicationClaim(application, request.getJDiscRequest().getUserPrincipal());
    }

}
