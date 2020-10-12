package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author ogronnesby
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuotaUsage {
    public static final QuotaUsage zero = new QuotaUsage() {{ this.rate = 0; }};

    public double rate;
}
