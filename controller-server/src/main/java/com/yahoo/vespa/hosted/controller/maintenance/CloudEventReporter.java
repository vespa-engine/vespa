// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.aws.AwsEventFetcher;
import com.yahoo.vespa.hosted.controller.api.integration.aws.CloudEvent;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueHandler;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Automatically fetches scheduled events from AWS and submits issues detailing them.
 *
 * @author mgimle
 */
public class CloudEventReporter extends Maintainer {

    private static final Logger log = Logger.getLogger(CloudEventReporter.class.getName());

    private final IssueHandler issueHandler;
    private final AwsEventFetcher eventFetcher;
    private final Set<String> awsRegions;

    CloudEventReporter(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
        this.issueHandler = controller.serviceRegistry().issueHandler();
        this.eventFetcher = controller.serviceRegistry().eventFetcherService();
        this.awsRegions = controller.zoneRegistry().zones()
                .ofCloud(CloudName.from("aws"))
                .reachable()
                .zones().stream()
                .map(ZoneApi::getCloudNativeRegionName)
                .collect(Collectors.toSet());
    }

    @Override
    protected void maintain() {
        log.log(Level.INFO, "Fetching events for cloud hosts.");
        for (var awsRegion : awsRegions) {
            List<CloudEvent> events = eventFetcher.getEvents(awsRegion);
            for (var event : events) {
                Issue issue = eventFetcher.createIssue(event);
                if (!issueHandler.issueExists(issue)) {
                    issueHandler.file(issue);
                    log.log(Level.INFO, String.format("Filed an issue with the title '%s'", issue.summary()));
                }
            }
        }
    }

}
