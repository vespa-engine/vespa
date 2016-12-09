// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;

import java.net.URI;

/**
 * Utilities for handlers.
 *
 * @author hmusum
 * @since 5.1.14
 */
// TODO: Dissolve into callers
public class Utils {

    /**
     * If request is an HTTP request and a jdisc request, return the {@link com.yahoo.jdisc.application.BindingMatch}
     * of the request. Otherwise return a dummy match useful only for testing based on the <code>uriPattern</code>
     * supplied.
     *
     * @param req        an {@link com.yahoo.container.jdisc.HttpRequest}
     * @param uriPattern a pattern to create a BindingMatch for in tests
     * @return match
     */
    public static BindingMatch<?> getBindingMatch(HttpRequest req, String uriPattern) {
        com.yahoo.jdisc.http.HttpRequest jDiscRequest = req.getJDiscRequest();
        BindingMatch<?> bm = jDiscRequest.getBindingMatch();
        if (bm == null) {
            bm = new BindingMatch<>(
                    new UriPattern(uriPattern).match(URI.create(jDiscRequest.getUri().toString())),
                    new Object());
        }
        return bm;
    }

    public static String getUrlBase(HttpRequest request, String pathPrefix) {
        return request.getUri().getScheme() + "://" + request.getHost() + ":" + request.getPort() + pathPrefix;
    }

    // Wrapper for getting the right exception if it fails
    public static Tenant checkThatTenantExists(Tenants tenants, TenantName tenantName) {
        try {
            return tenants.checkThatTenantExists(tenantName);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    // Wrapper for getting the right exception if it fails
    public static void checkThatTenantDoesNotExist(Tenants tenants, TenantName tenant) {
        try {
            tenants.checkThatTenantDoesNotExist(tenant);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    public static TenantName getTenantFromRequest(HttpRequest request) {
        BindingMatch<?> bm = getBindingMatch(request, "http://*/application/v2/tenant/*");
        return TenantName.from(bm.group(2));
    }

    public static TenantName getTenantFromSessionRequest(HttpRequest request) {
        BindingMatch<?> bm = getBindingMatch(request, "http://*/application/v2/tenant/*/session*");
        return TenantName.from(bm.group(2));
    }

    public static TenantName getTenantFromApplicationsRequest(HttpRequest request) {
        BindingMatch<?> bm = getBindingMatch(request, "http://*/application/v2/tenant/*/application*");
        return TenantName.from(bm.group(2));
    }
}
