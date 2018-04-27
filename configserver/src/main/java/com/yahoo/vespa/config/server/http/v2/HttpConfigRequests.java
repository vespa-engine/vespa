// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.net.URI;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.jdisc.application.UriPattern.Match;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.http.NotFoundException;

/**
 * Helpers for v2 config REST API
 *
 * @author vegardh
 */
public class HttpConfigRequests {

    static final String RECURSIVE_QUERY_PROPERTY = "recursive";
    
    /**
     * Produces the binding match for the request. If it's not available on the jDisc request, create one for
     * testing using the long and short app id URL patterns given.
     * @param req an {@link com.yahoo.container.jdisc.HttpRequest}
     * @param patterns A list of patterns that should be matched if no match on binding.
     * @return match
     */
    public static BindingMatch<?> getBindingMatch(HttpRequest req, String ... patterns) {
        com.yahoo.jdisc.http.HttpRequest jDiscRequest = req.getJDiscRequest();
        if (jDiscRequest==null) throw new IllegalArgumentException("No JDisc request for: " + req.getUri());
        BindingMatch<?> jdBm = jDiscRequest.getBindingMatch();
        if (jdBm!=null) return jdBm;

        // If not, use provided patterns
        for (String pattern : patterns) {
            UriPattern fullAppIdPattern = new UriPattern(pattern);
            URI uri = req.getUri();
            Match match = fullAppIdPattern.match(uri);
            if (match!=null) return new BindingMatch<>(match, new Object(), fullAppIdPattern);
        }
        throw new IllegalArgumentException("Illegal url for config request: " + req.getUri());
    }


    static RequestHandler getRequestHandler(TenantRepository tenantRepository, TenantRequest request) {
        Tenant tenant = tenantRepository.getTenant(request.getApplicationId().tenant());
        if (tenant==null) throw new NotFoundException("No such tenant: "+request.getApplicationId().tenant());
        return tenant.getRequestHandler();
    }
    
}
