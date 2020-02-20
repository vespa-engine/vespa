// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.aws.AwsEventFetcher;
import com.yahoo.vespa.hosted.controller.api.integration.aws.CloudEvent;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueHandler;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Automatically fetches and handles scheduled events from AWS:
 * 1. Deprovisions the affected hosts if applicable
 * 2. Submits an issue detailing the event if some hosts are not processed by 1.
 * @author mgimle
 */
public class CloudEventReporter extends Maintainer {

    private static final Logger log = Logger.getLogger(CloudEventReporter.class.getName());

    private final IssueHandler issueHandler;
    private final AwsEventFetcher eventFetcher;
    private final Map<String, List<ZoneApi>> zonesByCloudNativeRegion;
    private final NodeRepository nodeRepository;

    CloudEventReporter(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
        this.issueHandler = controller.serviceRegistry().issueHandler();
        this.eventFetcher = controller.serviceRegistry().eventFetcherService();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.zonesByCloudNativeRegion = getZonesByCloudNativeRegion();
    }

    @Override
    protected void maintain() {
        log.log(Level.INFO, "Fetching events for cloud hosts.");
        for (var awsRegion : zonesByCloudNativeRegion.keySet()) {
            List<CloudEvent> events = eventFetcher.getEvents(awsRegion);
            for (var event : events) {
                log.info(String.format("Retrieved event %s, affecting the following instances: %s",
                        event.instanceEventId,
                        event.affectedInstances));
                List<String> deprovisionedHosts = deprovisionHosts(awsRegion, event);
                submitIssue(event, deprovisionedHosts);
            }
        }
    }

    private List<String> deprovisionHosts(String awsRegion, CloudEvent event) {
        return zonesByCloudNativeRegion.get(awsRegion)
                .stream()
                .flatMap(zone ->
                    nodeRepository.list(zone.getId())
                            .stream()
                            .filter(shouldDeprovisionHost(event))
                            .map(node -> {
                                if (!node.wantToDeprovision() || !node.wantToRetire())
                                    log.info(String.format("Setting host %s to wantToRetire and wantToDeprovision", node.hostname().value()));
                                    nodeRepository.retireAndDeprovision(zone.getId(), node.hostname().value());
                                return node.hostname().value();
                            })
                )
                .collect(Collectors.toList());
    }

    private void submitIssue(CloudEvent event, List<String> deprovisionedHosts) {
        if (event.affectedInstances.size() == deprovisionedHosts.size())
            return;
        Issue issue = eventFetcher.createIssue(event);
        if (!issueHandler.issueExists(issue)) {
            issueHandler.file(issue);
            log.log(Level.INFO, String.format("Filed an issue with the title '%s'", issue.summary()));
        }
    }

    private Predicate<Node> shouldDeprovisionHost(CloudEvent event) {
        return node ->
                node.type() == NodeType.host &&
                event.affectedInstances.stream()
                        .anyMatch(instance -> node.hostname().value().contains(instance));
    }

    private Map<String, List<ZoneApi>> getZonesByCloudNativeRegion() {
        return controller().zoneRegistry().zones()
                .ofCloud(CloudName.from("aws"))
                .reachable()
                .zones().stream()
                .collect(Collectors.groupingBy(
                        ZoneApi::getCloudNativeRegionName
                ));
    }
}
