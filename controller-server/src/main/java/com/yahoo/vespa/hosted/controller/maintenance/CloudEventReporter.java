// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.aws.AwsEventFetcher;
import com.yahoo.vespa.hosted.controller.api.integration.aws.CloudEvent;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueHandler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Automatically fetches and handles scheduled events from AWS:
 * 1. Deprovisions the affected hosts if applicable
 * 2. Submits an issue detailing the event if some hosts are not processed by 1.
 *
 * @author mgimle
 */
public class CloudEventReporter extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(CloudEventReporter.class.getName());

    private final IssueHandler issueHandler;
    private final AwsEventFetcher eventFetcher;
    private final Map<String, List<ZoneApi>> zonesByCloudNativeRegion;
    private final NodeRepository nodeRepository;
    private final Metric metric;

    private static final String INFRASTRUCTURE_INSTANCE_EVENTS = "infrastructure_instance_events";

    CloudEventReporter(Controller controller, Duration interval, Metric metric) {
        super(controller, interval);
        this.issueHandler = controller.serviceRegistry().issueHandler();
        this.eventFetcher = controller.serviceRegistry().eventFetcherService();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.zonesByCloudNativeRegion = getZonesByCloudNativeRegion();
        this.metric = metric;
    }

    @Override
    protected boolean maintain() {
        int numberOfInfrastructureEvents = 0;
        for (var awsRegion : zonesByCloudNativeRegion.keySet()) {
            List<CloudEvent> events = eventFetcher.getEvents(awsRegion);
            for (var event : events) {
                log.info(String.format("Retrieved event %s, affecting the following instances: %s",
                        event.instanceEventId,
                        event.affectedInstances));
                List<Node> needsManualIntervention = handleInstances(awsRegion, event);
                if (!needsManualIntervention.isEmpty()) {
                    numberOfInfrastructureEvents += needsManualIntervention.size();
                    submitIssue(event);
                }
            }
        }
        metric.set(INFRASTRUCTURE_INSTANCE_EVENTS, numberOfInfrastructureEvents, metric.createContext(Collections.emptyMap()));
        return true;
    }

    /**
     * Handles affected instances in the following way:
     *  1. Ignore if unknown instance, presumably belongs to different system
     *  2. Retire and deprovision if tenant host
     *  3. Submit issue if infrastructure host, as it requires manual intervention
     */
    private List<Node> handleInstances(String awsRegion, CloudEvent event) {
        List<Node> needsManualIntervention = new ArrayList<>();
        for (var zone : zonesByCloudNativeRegion.get(awsRegion)) {
            for (var node : nodeRepository.list(zone.getId())) {
                if (!isAffected(node, event)){
                    continue;
                }
                if (node.type() == NodeType.host) {
                    log.info(String.format("Setting host %s to wantToRetire and wantToDeprovision", node.hostname().value()));
                    nodeRepository.retireAndDeprovision(zone.getId(), node.hostname().value());
                }
                else {
                    needsManualIntervention.add(node);
                }
            }
        }
        return needsManualIntervention;
    }

    private void submitIssue(CloudEvent event) {
        if (controller().system().isPublic())
            return;
        Issue issue = eventFetcher.createIssue(event);
        if (!issueHandler.issueExists(issue)) {
            issueHandler.file(issue);
            log.log(Level.INFO, String.format("Filed an issue with the title '%s'", issue.summary()));
        }
    }

    private boolean isAffected(Node node, CloudEvent event) {
        return event.affectedInstances.stream()
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
