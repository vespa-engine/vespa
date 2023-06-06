// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * @author bjorncs
 */
class CorsLogic {

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

    private final boolean allowAnyOrigin;
    private final Set<String> allowedOrigins;
    private final List<Pattern> allowedOriginPatterns;
    private CorsLogic(boolean allowAnyOrigin, Set<String> allowedOrigins, List<Pattern> allowedOriginPatterns) {
        this.allowAnyOrigin = allowAnyOrigin;
        this.allowedOrigins = Set.copyOf(allowedOrigins);
        this.allowedOriginPatterns = List.copyOf(allowedOriginPatterns);
    }

    boolean originMatches(String origin) {
        if (allowAnyOrigin) return true;
        if (allowedOrigins.contains(origin)) return true;
        return allowedOriginPatterns.stream().anyMatch(pattern -> pattern.matcher(origin).matches());
    }

    Map<String, String> createCorsResponseHeaders(String requestOriginHeader) {
        if (requestOriginHeader == null) return Map.of();

        TreeMap<String, String> headers = new TreeMap<>();
        if (originMatches(requestOriginHeader))
            headers.put(ALLOW_ORIGIN_HEADER, requestOriginHeader);
        headers.putAll(ACCESS_CONTROL_HEADERS);
        return headers;
    }

    Map<String, String> preflightResponseHeaders(String requestOriginHeader) {
        return createCorsResponseHeaders(requestOriginHeader);
    }

    static CorsLogic forAllowedOrigins(Collection<String> allowedOrigins) {
        Set<String> allowedOriginsVerbatim = new HashSet<>();
        List<Pattern> allowedOriginPatterns = new ArrayList<>();
        for (String allowedOrigin : allowedOrigins) {
            if (allowedOrigin.isBlank()) continue;
            if (allowedOrigin.length() > 0) {
                if ("*".equals(allowedOrigin))
                    return new CorsLogic(true, Set.of(), List.of());
                else if (allowedOrigin.contains("*"))
                    allowedOriginPatterns.add(Pattern.compile(allowedOrigin.replace(".", "\\.").replace("*", ".*")));
                else
                    allowedOriginsVerbatim.add(allowedOrigin);
            }
        }
        return new CorsLogic(false, allowedOriginsVerbatim, allowedOriginPatterns);
    }
}
