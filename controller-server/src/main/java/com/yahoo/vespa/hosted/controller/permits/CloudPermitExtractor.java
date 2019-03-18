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
public class CloudPermitExtractor implements PermitExtractor {

    @Override
    public CloudTenantPermit getTenantPermit(TenantName tenant, HttpRequest request) {
        return new CloudTenantPermit(tenant, request.getJDiscRequest().getUserPrincipal(), "token");
    }

    @Override
    public CloudApplicationPermit getApplicationPermit(ApplicationId application, HttpRequest request) {
        return new CloudApplicationPermit(application, request.getJDiscRequest().getUserPrincipal());
    }

}
