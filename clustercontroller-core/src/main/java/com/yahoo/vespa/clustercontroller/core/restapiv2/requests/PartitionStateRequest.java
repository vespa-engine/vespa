// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2.requests;

import com.yahoo.vdslib.state.DiskState;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.core.hostinfo.Metrics;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Id;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Request;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Response;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;

import java.util.Set;
import java.util.logging.Logger;

public class PartitionStateRequest extends Request<Response.PartitionResponse> {
    private static final Logger log = Logger.getLogger(PartitionStateRequest.class.getName());
    private final Id.Partition id;
    private final Set<VerboseReport> verboseReports;

    public PartitionStateRequest(Id.Partition id, Set<VerboseReport> verboseReports) {
        super(MasterState.MUST_BE_MASTER);
        this.id = id;
        this.verboseReports = verboseReports;
    }

    @Override
    public Response.PartitionResponse calculateResult(RemoteClusterControllerTask.Context context)
            throws StateRestApiException {
        Response.PartitionResponse result = new Response.PartitionResponse();
        if (verboseReports.contains(VerboseReport.STATISTICS)) {
            fillInMetrics(context.cluster.getNodeInfo(id.getNode()).getHostInfo().getMetrics(), result);
        }
        NodeState nodeState = context.currentConsolidatedState.getNodeState(id.getNode());
        DiskState diskState = nodeState.getDiskState(id.getPartitionIndex());
        result.addState("generated", new Response.UnitStateImpl(diskState));

        return result;
    }

    private static void fillInMetrics(Metrics metrics, Response.PartitionResponse result) {
        for (Metrics.Metric metric: metrics.getMetrics()) {
            fillInMetricValue(metric.getName(), metric.getValue(), result);
        }
    }

    private static void fillInMetricValue(
            String name, Metrics.Value value, Response.PartitionResponse result) {
        if (name.equals("vds.datastored.alldisks.docs")) {
            if (value.getLast() == null) {
                log.warning("Proper doc count value did not exist in value set.");
                return;
            }
            result.addMetric("unique-document-count", value.getLast());
        } else if (name.equals("vds.datastored.alldisks.bytes")) {
            if (value.getLast() == null) {
                log.warning("Proper doc size value did not exist in value set.");
                return;
            }
            result.addMetric("unique-document-total-size", value.getLast());
        } else if (name.equals("vds.datastored.alldisks.buckets")) {
            if (value.getLast() == null) {
                log.warning("Proper bucket count value did not exist in value set.");
                return;
            }
            result.addMetric("bucket-count", value.getLast());
        }
    }
}
