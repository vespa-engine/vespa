// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * @author Tony Vaagenes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrepareResponse {

    public String message;
    public List<Log> log;

}
