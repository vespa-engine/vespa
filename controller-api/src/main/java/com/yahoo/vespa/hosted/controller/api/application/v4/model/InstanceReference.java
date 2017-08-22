// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.vespa.hosted.controller.api.bcp.BcpStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.EnvironmentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;
import com.yahoo.vespa.hosted.controller.api.identifiers.RegionId;

import java.net.URI;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceReference {
    public EnvironmentId environment;
    public RegionId region;
    public InstanceId instance;
    public BcpStatus bcpStatus;

    public URI url;

    public static InstanceReference createInstanceReference(InstanceId instanceId, RegionId regionId, EnvironmentId environmentId, URI uri) {
        InstanceReference instanceReference = new InstanceReference();
        instanceReference.instance = instanceId;
        instanceReference.region = regionId;
        instanceReference.environment = environmentId;
        instanceReference.url = uri;
        return instanceReference;
    }
}
