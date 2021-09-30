// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;

import java.net.URI;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrepareResponse {
    public TenantId tenant;
    public String message;
    public List<Log> log;
    public ConfigChangeActions configChangeActions;
}
