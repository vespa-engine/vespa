// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantPipelinesInfo {
    public List<TenantPipelineInfo> tenantPipelines = new ArrayList<>();

    public static class TenantPipelineInfo {
        public String screwdriverId;
        public String tenant;
        public String application;
        public String instance;
    }

    public List<TenantPipelineInfo> brokenTenantPipelines = new ArrayList<>();
}
