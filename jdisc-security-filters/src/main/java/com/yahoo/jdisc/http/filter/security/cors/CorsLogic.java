// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author bjorncs
 */
class CorsLogic {
    private CorsLogic() {}

    static final String CORS_PREFLIGHT_REQUEST_CACHE_TTL = Long.toString(Duration.ofDays(7).getSeconds());

    static final String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";

    static final Map<String, String> ACCESS_CONTROL_HEADERS = Map.of(
            "Access-Control-Max-Age", CORS_PREFLIGHT_REQUEST_CACHE_TTL,
            "Access-Control-Allow-Headers", "Origin,Content-Type,Accept,Yahoo-Principal-Auth,Okta-Identity-Token," +
                    "Okta-Access-Token,Okta-Refresh-Token,Vespa-Csrf-Token",
            "Access-Control-Allow-Methods", "OPTIONS,GET,PUT,DELETE,POST,PATCH",
            "Access-Control-Allow-Credentials", "true",
            "Vary", "*"
    );

    static Map<String, String> createCorsResponseHeaders(String requestOriginHeader,
                                                         Set<String> allowedOrigins) {
        if (requestOriginHeader == null) return Map.of();

        TreeMap<String, String> headers = new TreeMap<>();
        if (requestOriginMatchesAnyAllowed(requestOriginHeader, allowedOrigins))
            headers.put(ALLOW_ORIGIN_HEADER, requestOriginHeader);
        headers.putAll(ACCESS_CONTROL_HEADERS);
        return headers;
    }

    static Map<String, String> createCorsPreflightResponseHeaders(String requestOriginHeader,
                                                                  Set<String> allowedOrigins) {
        return createCorsResponseHeaders(requestOriginHeader, allowedOrigins);
    }

    private static boolean requestOriginMatchesAnyAllowed(String requestOrigin, Set<String> allowedUrls) {
        return allowedUrls.stream().anyMatch(requestOrigin::equals) || allowedUrls.contains("*");
    }
}
