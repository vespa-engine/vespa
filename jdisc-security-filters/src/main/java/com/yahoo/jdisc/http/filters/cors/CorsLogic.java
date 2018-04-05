// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filters.cors;

import com.google.common.collect.ImmutableMap;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Response;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author bjorncs
 */
class CorsLogic {
    private CorsLogic() {}

    static final String CORS_PREFLIGHT_REQUEST_CACHE_TTL =  Long.toString(Duration.ofDays(7).getSeconds());

    static final String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";

    static final Map<String, String> ACCESS_CONTROL_HEADERS = ImmutableMap.of(
            "Access-Control-Max-Age", CORS_PREFLIGHT_REQUEST_CACHE_TTL,
            "Access-Control-Allow-Headers", "Origin,Content-Type,Accept,Yahoo-Principal-Auth",
            "Access-Control-Allow-Methods", "OPTIONS,GET,PUT,DELETE,POST",
            "Access-Control-Allow-Credentials", "true"
    );

    static Map<String, String> createCorsResponseHeaders(String requestOriginHeader,
                                                         Set<String> allowedOrigins) {
        if (requestOriginHeader == null) return Collections.emptyMap();
        TreeMap<String, String> headers = new TreeMap<>();
        allowedOrigins.stream()
                .filter(allowedUrl -> matchesRequestOrigin(requestOriginHeader, allowedUrl))
                .findAny()
                .ifPresent(allowedOrigin -> headers.put(ALLOW_ORIGIN_HEADER, allowedOrigin));
        ACCESS_CONTROL_HEADERS.forEach(headers::put);
        return headers;
    }

    static Map<String, String> createCorsPreflightResponseHeaders(String requestOriginHeader,
                                                                  Set<String> allowedOrigins) {
        TreeMap<String, String> headers = new TreeMap<>();
        if (allowedOrigins.contains(requestOriginHeader))
            headers.put(ALLOW_ORIGIN_HEADER, requestOriginHeader);
        ACCESS_CONTROL_HEADERS.forEach(headers::put);
        return headers;
    }

    private static boolean matchesRequestOrigin(String requestOrigin, String allowedUrl) {
        return allowedUrl.equals("*") || requestOrigin.startsWith(allowedUrl);
    }
}
