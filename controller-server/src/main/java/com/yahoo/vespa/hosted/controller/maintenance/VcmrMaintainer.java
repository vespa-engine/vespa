// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.Metric;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest.Impact;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestClient;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction.State;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VcmrReport;
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
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * Maintains status and execution of Vespa CMRs.
 *
 * Currently, this retires all affected tenant hosts if zone capacity allows it.
 *
 * @author olaa
 */
public class VcmrMaintainer extends ControllerMaintainer {

    private static final Logger LOG = Logger.getLogger(VcmrMaintainer.class.getName());
    private static final int DAYS_TO_RETIRE = 2;
    private static final Duration ALLOWED_POSTPONEMENT_TIME = Duration.ofDays(7);
    protected static final String TRACKED_CMRS_METRIC = "cmr.tracked";

    private final CuratorDb curator;
    private final NodeRepository nodeRepository;
    private final ChangeRequestClient changeRequestClient;
    private final SystemName system;
    private final Metric metric;

    public VcmrMaintainer(Controller controller, Duration interval, Metric metric) {
        super(controller, interval, null, SystemName.allOf(Predicate.not(SystemName::isPublic)));
        this.curator = controller.curator();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.changeRequestClient = controller.serviceRegistry().changeRequestClient();
        this.system = controller.system();
        this.metric = metric;
    }

    @Override
    protected double maintain() {
        var changeRequests = curator.readChangeRequests()
                .stream()
                .filter(shouldUpdate()).toList();

        var nodesByZone = nodesByZone();

        changeRequests.forEach(changeRequest -> {
            var nodes = impactedNodes(nodesByZone, changeRequest);
            var nextActions = getNextActions(nodes, changeRequest);
            var status = getStatus(nextActions, changeRequest);

            try (var lock = curator.lockChangeRequests()) {
                // Read the vcmr again, in case the source status has been updated
                curator.readChangeRequest(changeRequest.getId())
                        .ifPresent(vcmr -> {
                            var updatedVcmr = vcmr.withActionPlan(nextActions)
                                    .withStatus(status);
                            curator.writeChangeRequest(updatedVcmr);
                            if (nodes.keySet().size() == 1)
                                approveChangeRequest(updatedVcmr);
                        });
            }
        });
        updateMetrics();
        return 1.0;
    }

    /**
     * Status is based on:
     *  1. Whether the source has reportedly closed the request
     *  2. Whether any host requires operator action
     *  3. Whether any host is pending/started/finished retirement
     */
    private Status getStatus(List<HostAction> nextActions, VespaChangeRequest changeRequest) {
        if (changeRequest.getChangeRequestSource().isClosed()) {
            return Status.COMPLETED;
        }

        var byActionState = nextActions.stream().collect(Collectors.groupingBy(HostAction::getState, Collectors.counting()));

        if (byActionState.getOrDefault(State.REQUIRES_OPERATOR_ACTION, 0L) > 0) {
            return Status.REQUIRES_OPERATOR_ACTION;
        }

        if (byActionState.getOrDefault(State.OUT_OF_SYNC, 0L) > 0) {
            return Status.OUT_OF_SYNC;
        }

        if (byActionState.getOrDefault(State.RETIRING, 0L) > 0) {
            return Status.IN_PROGRESS;
        }

        if (Set.of(State.RETIRED, State.NONE).containsAll(byActionState.keySet())) {
            return Status.READY;
        }

        if (byActionState.getOrDefault(State.PENDING_RETIREMENT, 0L) > 0) {
            return Status.PENDING_ACTION;
        }

        return Status.NOOP;
    }

    private List<HostAction> getNextActions(Map<ZoneId, List<Node>> nodesByZone, VespaChangeRequest changeRequest) {
        return nodesByZone.entrySet()
                .stream()
                .flatMap(entry -> {
                    var zone = entry.getKey();
                    var nodes = entry.getValue();
                    if (nodes.isEmpty()) {
                        return Stream.empty();
                    }
                    var spareCapacity = hasSpareCapacity(zone, nodes);
                    return nodes.stream().map(node -> nextAction(zone, node, changeRequest, spareCapacity));
                }).toList();

    }

    // Get the superset of impacted hosts by looking at impacted switches
    private Map<ZoneId, List<Node>> impactedNodes(Map<ZoneId, List<Node>> nodesByZone, VespaChangeRequest changeRequest) {
        return nodesByZone.entrySet()
                .stream()
                .filter(entry -> entry.getValue().stream().anyMatch(isImpacted(changeRequest))) // Skip zones without impacted nodes
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().filter(isImpacted(changeRequest)).toList()
                ));
    }

    private Optional<HostAction> getPreviousAction(Node node, VespaChangeRequest changeRequest) {
        return changeRequest.getHostActionPlan()
                .stream()
                .filter(hostAction -> hostAction.getHostname().equals(node.hostname().value()))
                .findFirst();
    }

    private HostAction nextAction(ZoneId zoneId, Node node, VespaChangeRequest changeRequest, boolean spareCapacity) {
        var hostAction = getPreviousAction(node, changeRequest)
                .orElse(new HostAction(node.hostname().value(), State.NONE, Instant.now()));

        if (changeRequest.getChangeRequestSource().isClosed()) {
            LOG.fine(() -> changeRequest.getChangeRequestSource().getId() + " is closed, recycling " + node.hostname());
            recycleNode(zoneId, node, hostAction);
            removeReport(zoneId, changeRequest, node);
            return hostAction.withState(State.COMPLETE);
        }

        if (isLowImpact(changeRequest))
            return hostAction;

        addReport(zoneId, changeRequest, node);

        if (isOutOfSync(node, hostAction))
            return hostAction.withState(State.OUT_OF_SYNC);

        if (isPostponed(changeRequest, hostAction)) {
            LOG.fine(() -> changeRequest.getChangeRequestSource().getId() + " is postponed, recycling " + node.hostname());
            recycleNode(zoneId, node, hostAction);
            return hostAction.withState(State.PENDING_RETIREMENT);
        }

        if (node.type() != NodeType.host || !spareCapacity) {
            return hostAction.withState(State.REQUIRES_OPERATOR_ACTION);
        }

        if (shouldRetire(changeRequest, hostAction)) {
            if (!node.wantToRetire()) {
                LOG.info(Text.format("Retiring %s due to %s", node.hostname().value(), changeRequest.getChangeRequestSource().getId()));
                // TODO: Remove try/catch once retirement is stabilized
                try {
                    setWantToRetire(zoneId, node, true);
                } catch (Exception e) {
                    LOG.warning("Failed to retire host " + node.hostname() + ": " + Exceptions.toMessageString(e));
                    // Check if retirement actually failed
                    if (!nodeRepository.getNode(zoneId, node.hostname().value()).wantToRetire()) {
                        return hostAction;
                    }
                }
            }
            return hostAction.withState(State.RETIRING);
        }

        if (hasRetired(node, hostAction)) {
            LOG.fine(() -> node.hostname() + " has retired");
            return hostAction.withState(State.RETIRED);
        }

        if (pendingRetirement(node, hostAction)) {
            LOG.fine(() -> node.hostname() + " is pending retirement");
            return hostAction.withState(State.PENDING_RETIREMENT);
        }

        if (isFailed(node)) {
            return hostAction.withState(State.NONE);
        }

        return hostAction;
    }

    // Dirty host iff the parked host was retired by this maintainer
    private void recycleNode(ZoneId zoneId, Node node, HostAction hostAction) {
        if (hostAction.getState() == State.RETIRED &&
                node.state() == Node.State.parked) {
            LOG.info("Setting " + node.hostname() + " to dirty");
            nodeRepository.setState(zoneId, Node.State.dirty, node.hostname().value());
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
                getRetirementStartTime(changeRequest.getChangeRequestSource().getPlannedStartTime())
                        .isBefore(ZonedDateTime.now());
    }

    private boolean hasRetired(Node node, HostAction hostAction) {
        return List.of(State.RETIRING, State.REQUIRES_OPERATOR_ACTION).contains(hostAction.getState()) &&
                node.state() == Node.State.parked;
    }

    private boolean pendingRetirement(Node node, HostAction action) {
        return List.of(State.NONE, State.REQUIRES_OPERATOR_ACTION).contains(action.getState())
                && node.state() == Node.State.active;
    }

    // Determines if node state is unexpected based on previous action taken
    private boolean isOutOfSync(Node node, HostAction action) {
        return action.getState() == State.RETIRED && node.state() != Node.State.parked ||
                action.getState() == State.RETIRING && !node.wantToRetire();
    }

    private boolean isFailed(Node node) {
        return node.state() == Node.State.failed ||
                node.state() == Node.State.breakfixed;
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
                        zone -> nodeRepository.list(zone, NodeFilter.all())
                ));
    }

    private Predicate<Node> isImpacted(VespaChangeRequest changeRequest) {
        return node -> changeRequest.getImpactedHosts().contains(node.hostname().value()) ||
                node.switchHostname()
                        .map(switchHostname -> changeRequest.getImpactedSwitches().contains(switchHostname))
                        .orElse(false);
    }
    private Predicate<VespaChangeRequest> shouldUpdate() {
        return changeRequest -> changeRequest.getStatus() != Status.COMPLETED;
    }

    private boolean isLowImpact(VespaChangeRequest changeRequest) {
        return !List.of(Impact.HIGH, Impact.VERY_HIGH)
                .contains(changeRequest.getImpact());
    }

    private boolean hasSpareCapacity(ZoneId zoneId, List<Node> nodes) {
        var tenantHosts = nodes.stream()
                .filter(node -> node.type() == NodeType.host)
                .map(Node::hostname)
                .toList();

        return tenantHosts.isEmpty() ||
                nodeRepository.isReplaceable(zoneId, tenantHosts);
    }

    private void setWantToRetire(ZoneId zoneId, Node node, boolean wantToRetire) {
        nodeRepository.retire(zoneId, node.hostname().value(), wantToRetire, false);
    }

    private void approveChangeRequest(VespaChangeRequest changeRequest) {
        if (!system.equals(SystemName.main))
            return;
        if (changeRequest.getStatus() == Status.REQUIRES_OPERATOR_ACTION)
            return;
        if (changeRequest.getApproval() != ChangeRequest.Approval.REQUESTED)
            return;

        LOG.info("Approving " + changeRequest.getChangeRequestSource().getId());
        changeRequestClient.approveChangeRequest(changeRequest);
    }

    private void removeReport(ZoneId zoneId, VespaChangeRequest changeRequest, Node node) {
        var report = VcmrReport.fromReports(node.reports());

        if (report.removeVcmr(changeRequest.getChangeRequestSource().getId())) {
            updateReport(zoneId, node, report);
        }
    }

    private void addReport(ZoneId zoneId, VespaChangeRequest changeRequest, Node node) {
        var report = VcmrReport.fromReports(node.reports());

        var source = changeRequest.getChangeRequestSource();
        if (report.addVcmr(source.getId(), source.getPlannedStartTime(), source.getPlannedEndTime())) {
            updateReport(zoneId, node, report);
        }
    }

    private void updateReport(ZoneId zoneId, Node node, VcmrReport report) {
        LOG.fine(() -> Text.format("Updating report for %s: %s", node.hostname(), report));
        nodeRepository.updateReports(zoneId, node.hostname().value(), report.toNodeReports());
    }

    // Calculate wanted retirement start time, ignoring weekends
    // protected for testing
    protected ZonedDateTime getRetirementStartTime(ZonedDateTime plannedStartTime) {
        var time = plannedStartTime;
        var days = 0;
        while (days < DAYS_TO_RETIRE) {
            time = time.minusDays(1);
            if (time.getDayOfWeek().getValue() < 6) days++;
        }
        return time;
    }

    private void updateMetrics() {
        var cmrsByStatus = curator.readChangeRequests()
                .stream()
                .collect(Collectors.groupingBy(VespaChangeRequest::getStatus));

        for (var status : Status.values()) {
            var count = cmrsByStatus.getOrDefault(status, List.of()).size();
            metric.set(TRACKED_CMRS_METRIC, count, metric.createContext(Map.of("status", status.name())));
        }
    }

}
