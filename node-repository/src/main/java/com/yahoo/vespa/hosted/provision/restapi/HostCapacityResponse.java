// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.CapacityChecker;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @author mgimle
 */
public class HostCapacityResponse extends HttpResponse {

    private final StringBuilder text;
    private final Slime slime;
    private final CapacityChecker capacityChecker;
    private final boolean json;

    public HostCapacityResponse(NodeRepository nodeRepository, HttpRequest request) {
        super(200);
        capacityChecker = new CapacityChecker(nodeRepository.nodes().list());

        json = request.getBooleanProperty("json");
        String hostsJson = request.getProperty("hosts");

        text = new StringBuilder();
        slime = new Slime();
        Cursor root = slime.setObject();

        if (hostsJson != null) {
            List<Node> hosts = parseHostList(hostsJson);
            hostRemovalResponse(root, hosts);
        } else {
            zoneFailureReponse(root);
        }
    }

    private List<Node> parseHostList(String hosts) {
        List<String> hostNames = Arrays.asList(hosts.split(","));
        try {
            return capacityChecker.nodesFromHostnames(hostNames);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    private void hostRemovalResponse(Cursor root, List<Node> hosts) {
        var failure = capacityChecker.findHostRemovalFailure(hosts);
        if (failure.isPresent() && failure.get().failureReason.allocationFailures.size() == 0) {
            root.setBool("removalPossible", false);
            error(root, "Removing all hosts is trivially impossible.");
        } else {
            if (json) hostLossPossibleToSlime(root, failure, hosts);
            else      hostLossPossibleToText(failure, hosts);
        }
    }

    private void zoneFailureReponse(Cursor root) {
        var failurePath = capacityChecker.worstCaseHostLossLeadingToFailure();
        if (failurePath.isPresent()) {
            if (json) zoneFailurePathToSlime(root, failurePath.get());
            else      zoneFailurePathToText(failurePath.get());
        } else {
            error(root, "Node repository contained no hosts.");
        }
    }

    private void error(Cursor root, String errorMessage) {
        if (json) root.setString("error", errorMessage);
        else text.append(errorMessage);
    }

    private void hostLossPossibleToText(Optional<CapacityChecker.HostFailurePath> failure, List<Node> hostsToRemove) {
        text.append(String.format("Attempting to remove %d hosts: ", hostsToRemove.size()));
        CapacityChecker.AllocationHistory history = capacityChecker.allocationHistory;
        if (failure.isEmpty()) {
            text.append("OK\n\n");
            text.append(history);
            if (history.oldParents().size() != hostsToRemove.size()) {
                long emptyHostCount = hostsToRemove.size() - history.oldParents().size();
                text.append(String.format("\nTrivially removed %d empty host%s.", emptyHostCount, emptyHostCount > 1 ? "s" : ""));
            }
        } else {
            text.append("FAILURE\n\n");
            text.append(history).append("\n");
            text.append(failure.get().failureReason).append("\n\n");
       }
    }

    private void zoneFailurePathToText(CapacityChecker.HostFailurePath failurePath) {
        text.append(String.format("Found %d hosts. Failure upon trying to remove %d hosts:\n\n",
                capacityChecker.getHosts().size(),
                failurePath.hostsCausingFailure.size()));
        text.append(capacityChecker.allocationHistory).append("\n");
        text.append(failurePath.failureReason);
    }

    private void hostLossPossibleToSlime(Cursor root, Optional<CapacityChecker.HostFailurePath> failure, List<Node> hostsToRemove) {
        var hosts = root.setArray("hostsToRemove");
        hostsToRemove.forEach(h -> hosts.addString(h.hostname()));
        CapacityChecker.AllocationHistory history = capacityChecker.allocationHistory;
        root.setBool("removalPossible", failure.isEmpty());
        var arr = root.setArray("history");
        for (var entry : history.historyEntries) {
            var object = arr.addObject();
            object.setString("tenant", entry.tenant.hostname());
            if (entry.newParent != null) {
                object.setString("newParent", entry.newParent.hostname());
            }
            object.setLong("eligibleParents", entry.eligibleParents);
        }
    }

    private void zoneFailurePathToSlime(Cursor object, CapacityChecker.HostFailurePath failurePath) {
        object.setLong("totalHosts", capacityChecker.getHosts().size());
        object.setLong("couldLoseHosts", failurePath.hostsCausingFailure.size());
        failurePath.failureReason.host.ifPresent(host ->
                object.setString("failedTenantParent", host.hostname())
        );
        failurePath.failureReason.tenant.ifPresent(tenant -> {
            object.setString("failedTenant", tenant.hostname());
            object.setString("failedTenantResources", tenant.resources().toString());
            tenant.allocation().ifPresent(allocation ->
                    object.setString("failedTenantAllocation", allocation.toString())
            );
            var explanation = object.setObject("hostCandidateRejectionReasons");
            allocationFailureReasonListToSlime(explanation.setObject("singularReasonFailures"),
                    failurePath.failureReason.allocationFailures.singularReasonFailures());
            allocationFailureReasonListToSlime(explanation.setObject("totalFailures"),
                    failurePath.failureReason.allocationFailures);
        });
        var details = object.setObject("details");
        hostLossPossibleToSlime(details, Optional.of(failurePath), failurePath.hostsCausingFailure);
    }

    private void allocationFailureReasonListToSlime(Cursor root, CapacityChecker.AllocationFailureReasonList allocationFailureReasonList) {
        root.setLong("insufficientVcpu", allocationFailureReasonList.insufficientVcpu());
        root.setLong("insufficientMemoryGb", allocationFailureReasonList.insufficientMemoryGb());
        root.setLong("insufficientDiskGb", allocationFailureReasonList.insufficientDiskGb());
        root.setLong("incompatibleDiskSpeed", allocationFailureReasonList.incompatibleDiskSpeed());
        root.setLong("insufficientAvailableIps", allocationFailureReasonList.insufficientAvailableIps());
        root.setLong("violatesParentHostPolicy", allocationFailureReasonList.violatesParentHostPolicy());
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        if (json) new JsonFormat(true).encode(stream, slime);
        else stream.write(text.toString().getBytes());
    }

    @Override
    public String getContentType() {
        return json ? "application/json" : "text/plain";
    }
}
