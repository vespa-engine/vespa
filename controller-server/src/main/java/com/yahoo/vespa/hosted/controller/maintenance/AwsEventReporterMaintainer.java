package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.ZoneList;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.aws.AwsEventFetcher;
import com.yahoo.vespa.hosted.controller.api.integration.aws.CloudEvent;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueHandler;

import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author mgimle
 * Automatically fetches scheduled events from AWS and submits issues detailing them to Jira.
 */
public class AwsEventReporterMaintainer extends Maintainer {
    private static final Logger log = Logger.getLogger(AwsEventReporterMaintainer.class.getName());

    private final IssueHandler issueHandler;
    private final AwsEventFetcher eventFetcher;
    private final ZoneList cloudZones;

    AwsEventReporterMaintainer(Controller controller, Duration interval, JobControl jobControl,
                               IssueHandler issueHandler, AwsEventFetcher eventFetcher) {
        super(controller, interval, jobControl);
        this.cloudZones = awsZones(controller);
        this.issueHandler = issueHandler;
        this.eventFetcher = eventFetcher;
    }

    private ZoneList awsZones(Controller controller) {
        return controller.zoneRegistry().zones()
                .ofCloud(CloudName.from("aws"))
                .reachable();
    }

    @Override
    protected void maintain() {
        log.log(Level.INFO, "Fetching events for cloud hosts.");
        for (var cloudZoneId : cloudZones.ids()) {
            List<CloudEvent> events = eventFetcher.getEvents(cloudZoneId);
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
