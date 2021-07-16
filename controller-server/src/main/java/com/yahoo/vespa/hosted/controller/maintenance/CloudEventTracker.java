// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.aws.CloudEventFetcher;
import com.yahoo.vespa.hosted.controller.api.integration.aws.CloudEvent;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This tracks maintenance events from cloud providers and deprovisions any affected hosts.
 *
 * @author mgimle
 */
public class CloudEventTracker extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(CloudEventTracker.class.getName());

    private final CloudEventFetcher eventFetcher;
    private final Map<String, List<ZoneApi>> zonesByCloudNativeRegion;
    private final NodeRepository nodeRepository;

    CloudEventTracker(Controller controller, Duration interval) {
        super(controller, interval);
        this.eventFetcher = controller.serviceRegistry().eventFetcherService();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.zonesByCloudNativeRegion = supportedZonesByRegion();
    }

    @Override
    protected double maintain() {
        for (var region : zonesByCloudNativeRegion.keySet()) {
            List<CloudEvent> events = eventFetcher.getEvents(region);
            for (var event : events) {
                log.info(Text.format("Retrieved event %s, affecting the following instances: %s",
                                       event.instanceEventId,
                                       event.affectedInstances));
                deprovisionAffectedHosts(region, event);
            }
        }
        return 1.0;
    }

    /** Deprovision any host affected by given event */
    private void deprovisionAffectedHosts(String region, CloudEvent event) {
        for (var zone : zonesByCloudNativeRegion.get(region)) {
            for (var node : nodeRepository.list(zone.getId(), false)) {
                if (!affects(node, event)) continue;
                log.info("Retiring and deprovisioning " + node.hostname().value() + " in " + zone.getId() +
                         ": Affected by maintenance event " + event.instanceEventId);
                nodeRepository.retire(zone.getId(), node.hostname().value(), true, true);
            }
        }
    }

    private static boolean affects(Node node, CloudEvent event) {
        if (!node.type().isHost()) return false; // Non-hosts are never affected
        return event.affectedInstances.stream()
                                      .anyMatch(instance -> node.hostname().value().contains(instance));
    }

    /** Returns zones supported by this, grouped by their native region name */
    private Map<String, List<ZoneApi>> supportedZonesByRegion() {
        return controller().zoneRegistry().zones()
                           .ofCloud(CloudName.from("aws"))
                           .reachable()
                           .zones().stream()
                           .collect(Collectors.groupingBy(ZoneApi::getCloudNativeRegionName));
    }

}
