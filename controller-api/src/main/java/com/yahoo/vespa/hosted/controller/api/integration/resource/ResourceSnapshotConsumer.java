// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;

import java.util.Map;

/**
 * @author olaa
 */
public interface ResourceSnapshotConsumer {

    public void consume(Map<ApplicationId, ResourceSnapshot> resources);
}
