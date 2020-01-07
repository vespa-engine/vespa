// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;

public class MockAwsEventFetcher implements AwsEventFetcher {

    private Map<String, List<CloudEvent>> mockedEvents = new HashMap<>();

    @Override
    public List<CloudEvent> getEvents(String awsRegionName) {
        return mockedEvents.getOrDefault(awsRegionName, new ArrayList<>());
    }

    @Override
    public Issue createIssue(CloudEvent event) {
        return new Issue("summary", event.affectedInstances.toString(), "VESPA", Optional.empty()).with(User.from(event.awsRegionName));
    }

    public void addEvent(String awsRegionName, CloudEvent cloudEvent) {
        mockedEvents.computeIfAbsent(awsRegionName, i -> new ArrayList<>()).add(cloudEvent);
    }
}
