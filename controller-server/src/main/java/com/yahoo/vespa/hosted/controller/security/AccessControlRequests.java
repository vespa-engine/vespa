// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.slime.Inspector;

/**
 * Extracts {@link TenantSpec}s and {@link Credentials}s from HTTP requests, to be stored in an {@link AccessControl}.
 *
 * @author jonmv
 */
public interface AccessControlRequests {

    /** Extracts claim data for a tenant, from the given request. */
    TenantSpec specification(TenantName tenant, Inspector requestObject);

    /** Extracts credentials required for an access control modification for the given tenant, from the given request. */
    Credentials credentials(TenantName tenant, Inspector requestObject, HttpRequest jDiscRequest);

}
