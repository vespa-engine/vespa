// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.zone.RoutingMethod;

import java.net.URI;
import java.util.List;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceInformation {

    public List<Endpoint> endpoints;
    public URI yamasUrl;
    public Long deployTimeEpochMs;
    public Long expiryTimeEpochMs;

    public static class Endpoint {
        public String cluster;
        public boolean tls;
        public URI url;
        public String scope;
        public RoutingMethod routingMethod;
        public String auth;

        @JsonCreator
        public Endpoint(@JsonProperty("cluster") String cluster ,
                        @JsonProperty("tls") boolean tls,
                        @JsonProperty("url") URI url,
                        @JsonProperty("scope") String scope,
                        @JsonProperty("routingMethod") RoutingMethod routingMethod,
                        @JsonProperty("authMethod") String auth) {
            this.cluster = cluster;
            this.tls = tls;
            this.url = url;
            this.scope = scope;
            this.routingMethod = routingMethod;
            this.auth = auth;
        }

        @Override
        public String toString() {
            return "Endpoint{" +
                   "cluster='" + cluster + '\'' +
                   ", tls=" + tls +
                   ", url=" + url +
                   ", scope='" + scope + '\'' +
                   ", authType='" + auth + '\'' +
                   ", routingMethod=" + routingMethod +
                   '}';
        }
    }

}
