package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction.State;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VcmrReport;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest.Status;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author olaa
 */
public class VcmrMaintainerTest {

    private ControllerTester tester;
    private VcmrMaintainer maintainer;
    private NodeRepositoryMock nodeRepo;
    private final ZoneId zoneId = ZoneId.from("prod.us-east-3");
    private final HostName host1 = HostName.from("host1");
    private final HostName host2 = HostName.from("host2");
    private final String changeRequestId = "id123";

    @Before
    public void setup() {
        tester = new ControllerTester();
        maintainer = new VcmrMaintainer(tester.controller(), Duration.ofMinutes(1));
        nodeRepo = tester.serviceRegistry().configServer().nodeRepository().allowPatching(true);
    }

    @Test
    public void recycle_hosts_after_completion() {
        var vcmrReport = new VcmrReport();
        vcmrReport.addVcmr("id123", ZonedDateTime.now(), ZonedDateTime.now());
        var parkedNode = createNode(host1, NodeType.host, Node.State.parked, true);
        var failedNode = createNode(host2, NodeType.host, Node.State.failed, false);
        Map<String, String> reports = vcmrReport.toNodeReports().entrySet().stream()
                                                .collect(Collectors.toMap(Map.Entry::getKey,
                                                                          kv -> kv.getValue().toString()));
        parkedNode = Node.builder(parkedNode)
                         .reports(reports)
                         .build();

        nodeRepo.putNodes(zoneId, List.of(parkedNode, failedNode));

        tester.curator().writeChangeRequest(canceledChangeRequest());
        maintainer.maintain();

        // Only the parked node is recycled, VCMR report is cleared
        var nodeList = nodeRepo.list(zoneId, List.of(host1, host2));
        assertEquals(Node.State.dirty, nodeList.get(0).state());
        assertEquals(Node.State.failed, nodeList.get(1).state());

        assertTrue(nodeList.get(0).reports().isEmpty());

        var writtenChangeRequest = tester.curator().readChangeRequest(changeRequestId).get();
        assertEquals(Status.COMPLETED, writtenChangeRequest.getStatus());
    }

    @Test
    public void infrastructure_hosts_require_maunal_intervention() {
        var configNode = createNode(host1, NodeType.config, Node.State.active, false);
        var activeNode = createNode(host2, NodeType.host, Node.State.active, false);
        nodeRepo.putNodes(zoneId, List.of(configNode, activeNode));
        nodeRepo.hasSpareCapacity(true);

        tester.curator().writeChangeRequest(futureChangeRequest());
        maintainer.maintain();

        var writtenChangeRequest = tester.curator().readChangeRequest(changeRequestId).get();
        var configAction = writtenChangeRequest.getHostActionPlan().get(0);
        var tenantHostAction = writtenChangeRequest.getHostActionPlan().get(1);
        assertEquals(State.REQUIRES_OPERATOR_ACTION, configAction.getState());
        assertEquals(State.PENDING_RETIREMENT, tenantHostAction.getState());
        assertEquals(Status.REQUIRES_OPERATOR_ACTION, writtenChangeRequest.getStatus());
    }

    @Test
    public void retires_hosts_when_near_vcmr() {
        var activeNode = createNode(host1, NodeType.host, Node.State.active, false);
        var failedNode = createNode(host2, NodeType.host, Node.State.failed, false);
        nodeRepo.putNodes(zoneId, List.of(activeNode, failedNode));
        nodeRepo.hasSpareCapacity(true);

        tester.curator().writeChangeRequest(startingChangeRequest());
        maintainer.maintain();

        var writtenChangeRequest = tester.curator().readChangeRequest(changeRequestId).orElseThrow();
        var parkedNodeAction = writtenChangeRequest.getHostActionPlan().get(0);
        var failedNodeAction = writtenChangeRequest.getHostActionPlan().get(1);
        assertEquals(State.RETIRING, parkedNodeAction.getState());
        assertEquals(State.NONE, failedNodeAction.getState());
        assertEquals(Status.IN_PROGRESS, writtenChangeRequest.getStatus());

        activeNode = nodeRepo.list(zoneId, List.of(activeNode.hostname())).get(0);
        assertTrue(activeNode.wantToRetire());
    }

    @Test
    public void no_spare_capacity_requires_operator_action() {
        var activeNode = createNode(host1, NodeType.host, Node.State.active, false);
        var failedNode = createNode(host2, NodeType.host, Node.State.failed, false);
        nodeRepo.putNodes(zoneId, List.of(activeNode, failedNode));
        nodeRepo.hasSpareCapacity(false);

        tester.curator().writeChangeRequest(startingChangeRequest());
        maintainer.maintain();

        var writtenChangeRequest = tester.curator().readChangeRequest(changeRequestId).orElseThrow();
        var parkedNodeAction = writtenChangeRequest.getHostActionPlan().get(0);
        var failedNodeAction = writtenChangeRequest.getHostActionPlan().get(1);
        assertEquals(State.REQUIRES_OPERATOR_ACTION, parkedNodeAction.getState());
        assertEquals(State.REQUIRES_OPERATOR_ACTION, failedNodeAction.getState());
        assertEquals(Status.REQUIRES_OPERATOR_ACTION, writtenChangeRequest.getStatus());

        var approvedChangeRequests = tester.serviceRegistry().changeRequestClient().getApprovedChangeRequests();
        assertTrue(approvedChangeRequests.isEmpty());
    }

    @Test
    public void updates_status_when_retiring_host_is_parked() {
        var parkedNode = createNode(host1, NodeType.host, Node.State.parked, true);
        nodeRepo.putNodes(zoneId, parkedNode);
        nodeRepo.hasSpareCapacity(true);

        tester.curator().writeChangeRequest(inProgressChangeRequest());
        maintainer.maintain();

        var writtenChangeRequest = tester.curator().readChangeRequest(changeRequestId).orElseThrow();
        var parkedNodeAction = writtenChangeRequest.getHostActionPlan().get(0);
        assertEquals(State.RETIRED, parkedNodeAction.getState());
        assertEquals(Status.READY, writtenChangeRequest.getStatus());
    }

    @Test
    public void pending_retirement_when_vcmr_is_far_ahead() {
        var activeNode = createNode(host2, NodeType.host, Node.State.active, false);
        nodeRepo.putNodes(zoneId, List.of(activeNode));
        nodeRepo.hasSpareCapacity(true);

        tester.curator().writeChangeRequest(futureChangeRequest());
        maintainer.maintain();

        var writtenChangeRequest = tester.curator().readChangeRequest(changeRequestId).get();
        var tenantHostAction = writtenChangeRequest.getHostActionPlan().get(0);
        assertEquals(State.PENDING_RETIREMENT, tenantHostAction.getState());
        assertEquals(Status.PENDING_ACTION, writtenChangeRequest.getStatus());

        var approvedChangeRequests = tester.serviceRegistry().changeRequestClient().getApprovedChangeRequests();
        assertEquals(1, approvedChangeRequests.size());

        activeNode = nodeRepo.list(zoneId, List.of(host2)).get(0);
        var report = VcmrReport.fromReports(activeNode.reports());
        var reportAdded = report.getVcmrs().stream()
                        .filter(vcmr -> vcmr.getId().equals(changeRequestId))
                        .count() == 1;
        assertTrue(reportAdded);
    }

    @Test
    public void recycles_nodes_if_vcmr_is_postponed() {
        var parkedNode = createNode(host1, NodeType.host, Node.State.parked, false);
        var retiringNode = createNode(host2, NodeType.host, Node.State.active, true);
        nodeRepo.putNodes(zoneId, List.of(parkedNode, retiringNode));
        nodeRepo.hasSpareCapacity(true);

        tester.curator().writeChangeRequest(postponedChangeRequest());
        maintainer.maintain();

        var writtenChangeRequest = tester.curator().readChangeRequest(changeRequestId).get();
        var hostAction = writtenChangeRequest.getHostActionPlan().get(0);
        assertEquals(State.PENDING_RETIREMENT, hostAction.getState());

        parkedNode = nodeRepo.list(zoneId, List.of(parkedNode.hostname())).get(0);
        assertEquals(Node.State.dirty, parkedNode.state());
        assertFalse(parkedNode.wantToRetire());

        retiringNode = nodeRepo.list(zoneId, List.of(retiringNode.hostname())).get(0);
        assertEquals(Node.State.active, retiringNode.state());
        assertFalse(retiringNode.wantToRetire());
    }


    private VespaChangeRequest canceledChangeRequest() {
        return newChangeRequest(ChangeRequestSource.Status.CANCELED, State.RETIRED, State.RETIRING, ZonedDateTime.now());
    }

    private VespaChangeRequest futureChangeRequest() {
        return newChangeRequest(ChangeRequestSource.Status.WAITING_FOR_APPROVAL, State.NONE, State.NONE, ZonedDateTime.now().plus(Duration.ofDays(5L)));
    }

    private VespaChangeRequest startingChangeRequest() {
        return newChangeRequest(ChangeRequestSource.Status.STARTED, State.PENDING_RETIREMENT, State.NONE, ZonedDateTime.now());
    }

    private VespaChangeRequest inProgressChangeRequest() {
        return newChangeRequest(ChangeRequestSource.Status.STARTED, State.RETIRING, State.RETIRING, ZonedDateTime.now());
    }

    private VespaChangeRequest postponedChangeRequest() {
        return newChangeRequest(ChangeRequestSource.Status.STARTED, State.RETIRED, State.RETIRING, ZonedDateTime.now().plus(Duration.ofDays(8)));
    }


    private VespaChangeRequest newChangeRequest(ChangeRequestSource.Status sourceStatus, State state1, State state2, ZonedDateTime startTime) {
        var source = new ChangeRequestSource("aws", changeRequestId, "url", sourceStatus , startTime, ZonedDateTime.now());
        var actionPlan = List.of(
                new HostAction(host1.value(), state1, Instant.now()),
                new HostAction(host2.value(), state2, Instant.now())
        );
        return new VespaChangeRequest(
                changeRequestId,
                source,
                List.of("switch1"),
                List.of("host1", "host2"),
                ChangeRequest.Approval.REQUESTED,
                ChangeRequest.Impact.VERY_HIGH,
                VespaChangeRequest.Status.IN_PROGRESS,
                actionPlan,
                ZoneId.from("prod.us-east-3")
        );
    }

    private Node createNode(HostName hostname, NodeType nodeType, Node.State state, boolean wantToRetire) {
        return Node.builder()
                   .hostname(hostname)
                   .type(nodeType)
                   .state(state)
                   .wantToRetire(wantToRetire)
                   .build();
    }
}
