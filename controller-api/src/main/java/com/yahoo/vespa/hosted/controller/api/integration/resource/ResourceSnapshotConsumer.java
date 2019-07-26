// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import java.util.List;

/**
 * Consumes a snapshot of resourses allocated/used per application.
 *
 * @author olaa
 */
public interface ResourceSnapshotConsumer {

    public void consume(List<ResourceSnapshot> resources);

    public List<ResourceSnapshot> getResourceSnapshots(String tenantName, String applicationName);
}
