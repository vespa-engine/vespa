// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * @author Tony Vaagenes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstancesReply {
    public List<InstanceReference> instances;
    public String compileVersion;
}
