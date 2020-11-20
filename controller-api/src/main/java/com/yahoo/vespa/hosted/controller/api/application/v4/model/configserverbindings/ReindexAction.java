// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author jonmv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReindexAction {

    public final String name;
    public final String documentType;
    public final String clusterName;
    public final List<ServiceInfo> services;
    public final List<String> messages;

    @JsonCreator
    public ReindexAction(@JsonProperty("name") String name,
                         @JsonProperty("documentType") String documentType,
                         @JsonProperty("clusterName") String clusterName,
                         @JsonProperty("services") List<ServiceInfo> services,
                         @JsonProperty("messages") List<String> messages) {
        this.name = name;
        this.documentType = documentType;
        this.clusterName = clusterName;
        this.services = services;
        this.messages = messages;
    }

    @Override
    public String toString() {
        return "ReindexAction{" +
                "name='" + name + '\'' +
                ", documentType='" + documentType + '\'' +
                ", clusterName='" + clusterName + '\'' +
                ", services=" + services +
                ", messages=" + messages +
                '}';
    }

}
