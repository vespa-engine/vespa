// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.misc;

import com.yahoo.jdisc.http.filter.DiscFilterResponse;
import com.yahoo.jdisc.http.filter.RequestView;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;

/**
 * Adds recommended security response headers intended for hardening Rest APIs over https.
 *
 * @author bjorncs
 */
public class SecurityHeadersResponseFilter implements SecurityResponseFilter {

    @Override
    public void filter(DiscFilterResponse response, RequestView request) {
        response.setHeader("Cache-control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
