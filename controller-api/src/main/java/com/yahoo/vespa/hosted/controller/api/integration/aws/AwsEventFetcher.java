package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;

import java.util.List;

/**
 * @author freva
 */
public interface AwsEventFetcher {

    List<CloudEvent> getEvents(String awsRegionName);
    Issue createIssue(CloudEvent event);

}
