// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter.securitycontext;

import com.yahoo.vespa.hosted.controller.common.ContextAttributes;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * Get the security context from the underlying Servlet request, and expose it to
 * Jersey resources.
 *
 * @author Tony Vaagenes
 */
@PreMatching
@Provider
// TODO Remove once Bouncer filter is gone
@Deprecated
public class PropagateSecurityContextFilter implements ContainerRequestFilter {
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        SecurityContext securityContext =
            (SecurityContext) requestContext.getProperty(ContextAttributes.SECURITY_CONTEXT_ATTRIBUTE);

        if (securityContext != null) {
            requestContext.setSecurityContext(securityContext);
        }
    }
}
