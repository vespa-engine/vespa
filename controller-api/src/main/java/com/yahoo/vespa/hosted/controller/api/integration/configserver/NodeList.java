package com.yahoo.vespa.hosted.controller.api.integration.configserver;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author smorgrav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeList {

    @JsonProperty("nodes")
    public List<Node> nodes;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Node {
        @JsonProperty("hostname")
        public String hostname;
        @JsonProperty("flavor")
        public String flavor;
        @JsonProperty("membership")
        public Membership membership;
        @JsonProperty("cost")
        public int cost;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Membership {
            @JsonProperty("clustertype")
            public String clusterType;
            @JsonProperty("clusterid")
            public String clusterId;
        }
    }
}