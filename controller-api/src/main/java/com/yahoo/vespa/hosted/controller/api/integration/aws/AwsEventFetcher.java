package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;

import java.util.List;

public interface AwsEventFetcher {
    List<CloudEvent> getEvents(ZoneId zoneId);
    Issue createIssue(CloudEvent event);
}
