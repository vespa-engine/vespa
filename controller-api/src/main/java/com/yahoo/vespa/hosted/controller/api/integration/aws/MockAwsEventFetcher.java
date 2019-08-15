package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;

import java.util.List;
import java.util.Optional;

public class MockAwsEventFetcher implements AwsEventFetcher {
    @Override
    public List<CloudEvent> getEvents(String awsRegionName) {
        return List.of();
    }

    @Override
    public Issue createIssue(CloudEvent event) {
        return new Issue("summary", "description", "VESPA", Optional.empty());
    }
}
