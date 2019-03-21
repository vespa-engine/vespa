package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.slime.Inspector;

import java.security.Principal;

/**
 * Extracts {@link TenantClaim}s and {@link ApplicationClaim}s from HTTP requests, to be stored in an {@link AccessControl}.
 *
 * @author jonmv
 */
public interface Claims {

    /** Extracts claim data for a tenant, from the given request. */
    TenantClaim getTenantClaim(TenantName tenant, Inspector requestObject);

    /** Extracts credentials required for an access control modification for the given tenant, from the given request. */
    Credentials getCredentials(TenantName tenant, Inspector requestObject, HttpRequest jDiscRequest);

}
