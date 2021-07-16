// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author freva
 */
public class MockCloudEventFetcher implements CloudEventFetcher {

    private final Map<String, List<CloudEvent>> mockedEvents = new HashMap<>();

    @Override
    public List<CloudEvent> getEvents(String regionName) {
        return mockedEvents.getOrDefault(regionName, new ArrayList<>());
    }

    public void addEvent(String regionName, CloudEvent cloudEvent) {
        mockedEvents.computeIfAbsent(regionName, i -> new ArrayList<>()).add(cloudEvent);
    }

}
