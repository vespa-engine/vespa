// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationId;

import java.util.Map;

/**
 * Consumes a snapshot of resourses allocated/used per application.
 *
 * @author olaa
 */
public interface ResourceSnapshotConsumer {

    public void consume(Map<ApplicationId, ResourceSnapshot> resources);
}
