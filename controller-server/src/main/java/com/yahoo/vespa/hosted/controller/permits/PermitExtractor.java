package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.container.jdisc.HttpRequest;

/**
 * Extracts {@link TenantPermit}s and {@link ApplicationPermit}s from HTTP requests, to be stored in a {@link PermitStore}.
 *
 * @author jonmv
 */
public interface PermitExtractor {

    /** Extracts permit data for a tenant, from the given request. */
    TenantPermit getTenantPermit(HttpRequest request);

    /** Extracts permit data for an application, from the given request. */
    ApplicationPermit getApplication(HttpRequest request);

}
