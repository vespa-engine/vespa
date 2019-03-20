package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;

/**
 * Extracts {@link TenantClaim}s and {@link ApplicationClaim}s from HTTP requests, to be stored in an {@link AccessControl}.
 *
 * @author jonmv
 */
public interface Claims {

    /** Extracts claim data for a tenant, from the given request. */
    TenantClaim getTenantClaim(TenantName tenant, HttpRequest request);

    /** Extracts claim data for an application, from the given request. */
    ApplicationClaim getApplicationClaim(ApplicationId application, HttpRequest request);

}
