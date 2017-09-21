// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost.restapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * JSON datamodel for the cost api.
 *
 * @author smorgrav
 */
public class CostJsonModel {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Application {

        @JsonProperty
        public String zone;
        @JsonProperty
        public String tenant;
        @JsonProperty
        public String app;
        @JsonProperty
        public int tco;
        @JsonProperty
        public double utilization;
        @JsonProperty
        public double waste;
        @JsonProperty
        public Map<String, Cluster> cluster;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cluster {

        @JsonProperty
        public int count;
        @JsonProperty
        public String resource;
        @JsonProperty
        public double utilization;
        @JsonProperty
        public int tco;
        @JsonProperty
        public String flavor;
        @JsonProperty
        public int waste;
        @JsonProperty
        public String type;
        @JsonProperty
        public HardwareResources util;
        @JsonProperty
        public HardwareResources usage;
        @JsonProperty
        public List<String> hostnames;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HardwareResources {

        @JsonProperty
        public double mem;
        @JsonProperty
        public double disk;
        @JsonProperty
        public double cpu;
        @JsonProperty("diskbusy")
        public double diskBusy;
    }
}
