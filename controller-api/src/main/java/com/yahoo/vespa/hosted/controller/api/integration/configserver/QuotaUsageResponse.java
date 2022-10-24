// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author ogronnesby
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuotaUsageResponse {
    public static final QuotaUsageResponse zero = new QuotaUsageResponse() {{ this.rate = 0; }};

    public double rate;
}
