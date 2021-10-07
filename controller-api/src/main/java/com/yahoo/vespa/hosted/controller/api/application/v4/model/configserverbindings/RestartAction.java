// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestartAction {
    public final String clusterName;
    public final String clusterType;
    public final String serviceType;
    public final List<ServiceInfo> services;
    public final List<String> messages;

    @JsonCreator
    public RestartAction(@JsonProperty("clusterName") String clusterName,
                         @JsonProperty("clusterType") String clusterType,
                         @JsonProperty("serviceType") String serviceType,
                         @JsonProperty("services") List<ServiceInfo> services,
                         @JsonProperty("messages") List<String> messages) {
        this.clusterName = clusterName;
        this.clusterType = clusterType;
        this.serviceType = serviceType;
        this.services = services;
        this.messages = messages;
    }

    @Override
    public String toString() {
        return "RestartAction{" +
                "clusterName='" + clusterName + '\'' +
                ", clusterType='" + clusterType + '\'' +
                ", serviceType='" + serviceType + '\'' +
                ", services=" + services +
                ", messages=" + messages +
                '}';
    }
}
