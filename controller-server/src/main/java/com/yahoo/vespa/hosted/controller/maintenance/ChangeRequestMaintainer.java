// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestClient;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class ChangeRequestMaintainer extends ControllerMaintainer {

    private final Logger logger = Logger.getLogger(ChangeRequestMaintainer.class.getName());
    private final ChangeRequestClient changeRequestClient;
    private final CuratorDb curator;
    private final NodeRepository nodeRepository;
    private final SystemName system;

    public ChangeRequestMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(Predicate.not(SystemName::isPublic)));
        this.changeRequestClient = controller.serviceRegistry().changeRequestClient();
        this.curator = controller.curator();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.system = controller.system();
    }


    @Override
    protected double maintain() {
        var currentChangeRequests = pruneOldChangeRequests();
        var changeRequests = changeRequestClient.getChangeRequests(currentChangeRequests);

        logger.fine(() -> "Found requests: " + changeRequests);
        storeChangeRequests(changeRequests);

        return 1.0;
    }

    private void storeChangeRequests(List<ChangeRequest> changeRequests) {
        var existingChangeRequests = curator.readChangeRequests()
                .stream()
                .collect(Collectors.toMap(ChangeRequest::getId, Function.identity()));

        var hostsByZone = hostsByZone();
        // Create or update requests in curator
        try (var lock = curator.lockChangeRequests()) {
            changeRequests.forEach(changeRequest -> {
                var optionalZone = inferZone(changeRequest, hostsByZone);
                optionalZone.ifPresentOrElse(zone -> {
                    var vcmr = existingChangeRequests
                            .getOrDefault(changeRequest.getId(), new VespaChangeRequest(changeRequest, zone))
                            .withSource(changeRequest.getChangeRequestSource())
                            .withImpact(changeRequest.getImpact())
                            .withApproval(changeRequest.getApproval());
                    logger.fine(() -> "Storing " + vcmr);
                    curator.writeChangeRequest(vcmr);
                },
                () -> approveChangeRequest(changeRequest));
            });
        }
    }

    // Deletes closed change requests older than 7 days, returns the current list of requests
    private List<ChangeRequest> pruneOldChangeRequests() {
        List<ChangeRequest> currentChangeRequests = new ArrayList<>();

        try (var lock = curator.lockChangeRequests()) {
            for (var changeRequest : curator.readChangeRequests()) {
                if (shouldDeleteChangeRequest(changeRequest.getChangeRequestSource())) {
                    curator.deleteChangeRequest(changeRequest);
                } else {
                    currentChangeRequests.add(changeRequest);
                }
            }
        }
        return currentChangeRequests;
    }

    private Map<ZoneId, List<String>> hostsByZone() {
        return controller().zoneRegistry()
                .zones()
                .reachable()
                .in(Environment.prod)
                .ids()
                .stream()
                .collect(Collectors.toMap(
                        zone -> zone,
                        zone -> nodeRepository.list(zone, NodeFilter.all())
                                .stream()
                                .map(node -> node.hostname().value())
                                .collect(Collectors.toList())
                ));
    }

    private Optional<ZoneId> inferZone(ChangeRequest changeRequest, Map<ZoneId, List<String>> hostsByZone) {
        return hostsByZone.entrySet().stream()
                .filter(entry -> !Collections.disjoint(entry.getValue(), changeRequest.getImpactedHosts()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private boolean shouldDeleteChangeRequest(ChangeRequestSource source) {
        return source.isClosed() &&
                source.getPlannedStartTime()
                        .plus(Duration.ofDays(7))
                        .isBefore(ZonedDateTime.now());
    }

    private void approveChangeRequest(ChangeRequest changeRequest) {
        if (system.equals(SystemName.main) &&
                changeRequest.getApproval() == ChangeRequest.Approval.REQUESTED) {
            logger.info("Approving " + changeRequest.getChangeRequestSource().getId());
            changeRequestClient.approveChangeRequest(changeRequest);
        }
    }
}
