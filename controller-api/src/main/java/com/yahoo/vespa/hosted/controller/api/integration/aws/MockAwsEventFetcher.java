package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;

import java.util.List;
import java.util.Optional;

public class MockAwsEventFetcher implements AwsEventFetcher {
    @Override
    public List<CloudEvent> getEvents(ZoneId zoneId) {
        return List.of();
    }

    @Override
    public Issue createIssue(CloudEvent event) {
        return new Issue("summary", "description", "VESPA", Optional.empty());
    }
}
