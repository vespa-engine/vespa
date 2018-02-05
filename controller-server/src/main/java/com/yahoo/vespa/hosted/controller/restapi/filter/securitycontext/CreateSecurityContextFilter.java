// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter.securitycontext;

import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.hosted.controller.common.ContextAttributes;
import com.yahoo.yolean.chain.After;
import com.yahoo.yolean.chain.Provides;

import javax.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * Exposes the security information from the disc filter request
 * by storing a security context in the request context.
 *
 * @author Tony Vaagenes
 */
@After("BouncerFilter")
@Provides("SecurityContext")
@SuppressWarnings("unused") // Injected
@Deprecated
// TODO Remove once Bouncer filter is gone
public class CreateSecurityContextFilter implements SecurityRequestFilter {

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        request.setAttribute(ContextAttributes.SECURITY_CONTEXT_ATTRIBUTE,
                new SecurityContext() {
                    @Override
                    public Principal getUserPrincipal() {
                        return request.getUserPrincipal();
                    }

                    @Override
                    public boolean isUserInRole(String role) {
                        return request.isUserInRole(role);
                    }

                    @Override
                    public boolean isSecure() {
                        return request.isSecure();
                    }

                    @Override
                    public String getAuthenticationScheme() {
                        throw new UnsupportedOperationException();
                    }
                });
    }
    
}
