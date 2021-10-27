// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;

import java.util.Optional;
import java.util.Set;

/**
 * Extracts access control data for {@link CloudTenant}s from HTTP requests.
 *
 * @author jonmv
 * @author andreer
 */
public class CloudAccessControlRequests implements AccessControlRequests {

    @Override
    public CloudTenantSpec specification(TenantName tenant, Inspector requestObject) {
        return new CloudTenantSpec(tenant, "token"); // TODO: remove token
    }

    @Override
    public Credentials credentials(TenantName tenant, Inspector requestObject, HttpRequest request) {
        return new Auth0Credentials(request.getUserPrincipal(), getUserRoles(request));
    }

    private static Set<Role> getUserRoles(HttpRequest request) {
        var securityContext = Optional.ofNullable(request.context().get(SecurityContext.ATTRIBUTE_NAME))
                .filter(SecurityContext.class::isInstance)
                .map(SecurityContext.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Attribute '" + SecurityContext.ATTRIBUTE_NAME + "' was not set on request"));
        return securityContext.roles();
    }

}
