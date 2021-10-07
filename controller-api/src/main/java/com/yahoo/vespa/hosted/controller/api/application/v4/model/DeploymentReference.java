// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.vespa.hosted.controller.api.identifiers.EnvironmentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.RegionId;

import java.net.URI;

/**
 * @author jonmv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentReference {
    public EnvironmentId environment;
    public RegionId region;

    public URI url;

}
