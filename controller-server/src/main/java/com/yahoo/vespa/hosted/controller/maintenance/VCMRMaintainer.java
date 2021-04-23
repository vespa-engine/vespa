// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest.Impact;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction.State;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest.Status;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author olaa
 *
 * Maintains status and execution of VCMRs
 * For now only retires all affected tenant hosts if zone capacity allows it
 */
public class VCMRMaintainer extends ControllerMaintainer {

    private final Logger logger = Logger.getLogger(VCMRMaintainer.class.getName());
    private final Duration ALLOWED_RETIREMENT_TIME = Duration.ofHours(60);
    private final Duration ALLOWED_POSTPONEMENT_TIME = Duration.ofDays(7);
    private final CuratorDb curator;
    private final NodeRepository nodeRepository;

    public VCMRMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(Predicate.not(SystemName::isPublic)));
        this.curator = controller.curator();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
    }

    @Override
    protected boolean maintain() {
        var changeRequests = curator.readChangeRequests()
                .stream()
                .filter(shouldUpdate())
                .collect(Collectors.toList());

        var nodesByZone = nodesByZone();

        changeRequests.forEach(changeRequest -> {
            var nodes = impactedNodes(nodesByZone, changeRequest);
            var nextActions = getNextActions(nodes, changeRequest);
            var status = getStatus(nextActions, changeRequest);

            try (var lock = curator.lockChangeRequests()) {
                // Read the vcmr again, in case the source status has been updated
                curator.readChangeRequest(changeRequest.getId())
                        .ifPresent(vcmr -> curator.writeChangeRequest(vcmr.withActionPlan(nextActions)
                                                                            .withStatus(status)));
            }
        });

        return true;
    }

    /**
     * Status is based on:
     *  1. Whether the source has reportedly closed the request
     *  2. Whether any host requires operator action
     *  3. Whether any host has started/finished retiring
     */
    private Status getStatus(List<HostAction> nextActions, VespaChangeRequest changeRequest) {
        if (changeRequest.getChangeRequestSource().isClosed()) {
            return Status.COMPLETED;
        }

        var byActionState = nextActions.stream().collect(Collectors.groupingBy(HostAction::getState, Collectors.counting()));

        if (byActionState.getOrDefault(State.REQUIRES_OPERATOR_ACTION, 0L) > 0) {
            return Status.REQUIRES_OPERATOR_ACTION;
        }

        if (byActionState.getOrDefault(State.RETIRING, 0L) + byActionState.getOrDefault(State.RETIRED, 0L) > 0) {
            return Status.IN_PROGRESS;
        }

        if (byActionState.getOrDefault(State.PENDING_RETIREMENT, 0L) > 0) {
            return Status.PENDING_ACTION;
        }

        return Status.NOOP;
    }

    private List<HostAction> getNextActions(List<Node> nodes, VespaChangeRequest changeRequest) {
        var spareCapacity = hasSpareCapacity(changeRequest.getZoneId(), nodes);
        return nodes.stream()
                .map(node -> nextAction(node, changeRequest, spareCapacity))
                .collect(Collectors.toList());
    }

    // Get the superset of impacted hosts by looking at impacted switches
    private List<Node> impactedNodes(Map<ZoneId, List<Node>> nodesByZone, VespaChangeRequest changeRequest) {
        return nodesByZone.get(changeRequest.getZoneId())
                .stream()
                .filter(isImpacted(changeRequest))
                .collect(Collectors.toList());
    }

    private Optional<HostAction> getPreviousAction(Node node, VespaChangeRequest changeRequest) {
        return changeRequest.getHostActionPlan()
                .stream()
                .filter(hostAction -> hostAction.getHostname().equals(node.hostname().value()))
                .findFirst();
    }

    private HostAction nextAction(Node node, VespaChangeRequest changeRequest, boolean spareCapacity) {
        var hostAction = getPreviousAction(node, changeRequest)
                .orElse(new HostAction(node.hostname().value(), State.NONE, Instant.now()));

        if (changeRequest.getChangeRequestSource().isClosed()) {
            logger.fine(() -> changeRequest.getChangeRequestSource().getId() + " is closed, recycling " + node.hostname());
            recycleNode(changeRequest.getZoneId(), node, hostAction);
            return hostAction.withState(State.COMPLETE);
        }

        if (isPostponed(changeRequest, hostAction)) {
            logger.fine(() -> changeRequest.getChangeRequestSource().getId() + " is postponed, recycling " + node.hostname());
            recycleNode(changeRequest.getZoneId(), node, hostAction);
            return hostAction.withState(State.PENDING_RETIREMENT);
        }

        if (node.type() != NodeType.host || !spareCapacity) {
            return hostAction.withState(State.REQUIRES_OPERATOR_ACTION);
        }

        if (shouldRetire(changeRequest, hostAction)) {
            if (!node.wantToRetire()) {
                logger.info(String.format("Retiring %s due to %s", node.hostname().value(), changeRequest.getChangeRequestSource().getId()));
                // TODO: Remove try/catch once retirement is stabilized
                try {
                    setWantToRetire(changeRequest.getZoneId(), node, true);
                } catch (Exception e) {
                    logger.warning("Failed to retire host " + node.hostname() + ": " + Exceptions.toMessageString(e));
                    // Check if retirement actually failed
                    if (!nodeRepository.getNode(changeRequest.getZoneId(), node.hostname().value()).getWantToRetire()) {
                        return hostAction;
                    }
                }
            }
            return hostAction.withState(State.RETIRING);
        }

        if (hasRetired(node, hostAction)) {
            logger.fine(() -> node.hostname() + " has retired");
            return hostAction.withState(State.RETIRED);
        }

        if (pendingRetirement(node, hostAction)) {
            logger.fine(() -> node.hostname() + " is pending retirement");
            return hostAction.withState(State.PENDING_RETIREMENT);
        }

        return hostAction;
    }

    // Dirty host iff the parked host was retired by this maintainer
    private void recycleNode(ZoneId zoneId, Node node, HostAction hostAction) {
        if (hostAction.getState() == State.RETIRED &&
                node.state() == Node.State.parked) {
            logger.info("Setting " + node.hostname() + " to dirty");
            nodeRepository.setState(zoneId, NodeState.dirty, node.hostname().value());
        }
        if (hostAction.getState() == State.RETIRING && node.wantToRetire()) {
            try {
                setWantToRetire(zoneId, node, false);
            } catch (Exception ignored) {}
        }
    }

    private boolean isPostponed(VespaChangeRequest changeRequest, HostAction action) {
        return List.of(State.RETIRED, State.RETIRING).contains(action.getState()) &&
                changeRequest.getChangeRequestSource().getPlannedStartTime()
                        .minus(ALLOWED_POSTPONEMENT_TIME)
                        .isAfter(ZonedDateTime.now());
    }

    private boolean shouldRetire(VespaChangeRequest changeRequest, HostAction action) {
        return action.getState() == State.PENDING_RETIREMENT &&
                changeRequest.getChangeRequestSource().getPlannedStartTime()
                        .minus(ALLOWED_RETIREMENT_TIME)
                        .isBefore(ZonedDateTime.now());
    }

    private boolean hasRetired(Node node, HostAction hostAction) {
        return hostAction.getState() == State.RETIRING &&
                node.state() == Node.State.parked;
    }

    /**
     * TODO: For now, we choose to retire any active host
     */
    private boolean pendingRetirement(Node node, HostAction action) {
        return action.getState() == State.NONE && node.state() == Node.State.active;
    }

    private Map<ZoneId, List<Node>> nodesByZone() {
        return controller().zoneRegistry()
                .zones()
                .reachable()
                .in(Environment.prod)
                .ids()
                .stream()
                .collect(Collectors.toMap(
                        zone -> zone,
                        zone -> nodeRepository.list(zone, false)
                ));
    }

    private Predicate<Node> isImpacted(VespaChangeRequest changeRequest) {
        return node -> changeRequest.getImpactedHosts().contains(node.hostname().value()) ||
                node.switchHostname()
                        .map(switchHostname -> changeRequest.getImpactedSwitches().contains(switchHostname))
                        .orElse(false);
    }
    private Predicate<VespaChangeRequest> shouldUpdate() {
        return changeRequest -> changeRequest.getStatus() != Status.COMPLETED &&
                 List.of(Impact.HIGH, Impact.VERY_HIGH)
                         .contains(changeRequest.getImpact());
    }

    private boolean hasSpareCapacity(ZoneId zoneId, List<Node> nodes) {
        var tenantHosts = nodes.stream()
                .filter(node -> node.type() == NodeType.host)
                .map(Node::hostname)
                .collect(Collectors.toList());

        return tenantHosts.isEmpty() ||
                nodeRepository.isReplaceable(zoneId, tenantHosts);
    }

    private void setWantToRetire(ZoneId zoneId, Node node, boolean wantToRetire) {
        var newNode = new NodeRepositoryNode();
        newNode.setWantToRetire(wantToRetire);
        nodeRepository.patchNode(zoneId, node.hostname().value(), newNode);
    }
}
