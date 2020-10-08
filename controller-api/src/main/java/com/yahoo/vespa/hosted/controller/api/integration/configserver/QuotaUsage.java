package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author ogronnesby
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuotaUsage {
    public double rate;
}
