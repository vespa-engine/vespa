// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.zone.AuthMethod;
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

    public record Endpoint(@JsonProperty("cluster") String cluster,
                           @JsonProperty("tls") boolean tls,
                           @JsonProperty("url") URI url,
                           @JsonProperty("scope") String scope,
                           @JsonProperty("routingMethod") RoutingMethod routingMethod,
                           @JsonProperty("authMethod") AuthMethod auth,
                           @JsonProperty("legacy") boolean legacy) {
    }
}
