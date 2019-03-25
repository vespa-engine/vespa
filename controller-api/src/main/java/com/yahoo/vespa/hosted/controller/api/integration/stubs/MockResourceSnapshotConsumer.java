// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshotConsumer;

import java.util.Map;

/**
 * @author olaa
 */
public class MockResourceSnapshotConsumer implements ResourceSnapshotConsumer {

    @Override
    public void consume(Map<ApplicationId, ResourceSnapshot> resources){}

}
