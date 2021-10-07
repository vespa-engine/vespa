// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceReference {

    public List<DeploymentReference> deployments;
    public InstanceId instance;
    public Set<URI> globalRotations;
    public String rotationId;
    public URI url;

}
