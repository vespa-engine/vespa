// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.util.Map;

/**
 * @author gv
 */
public interface AccessControlHeaders {

    String CORS_PREFLIGHT_REQUEST_CACHE_TTL =  Long.toString(Duration.ofDays(7).getSeconds());

    String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";

    Map<String, String> ACCESS_CONTROL_HEADERS = ImmutableMap.of(
            "Access-Control-Max-Age", CORS_PREFLIGHT_REQUEST_CACHE_TTL,
            "Access-Control-Allow-Headers", "Origin,Content-Type,Accept,Yahoo-Principal-Auth",
            "Access-Control-Allow-Methods", "OPTIONS,GET,PUT,DELETE,POST",
            "Access-Control-Allow-Credentials", "true"
    );

}
