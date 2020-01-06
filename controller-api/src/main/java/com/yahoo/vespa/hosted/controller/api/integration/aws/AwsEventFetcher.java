// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
