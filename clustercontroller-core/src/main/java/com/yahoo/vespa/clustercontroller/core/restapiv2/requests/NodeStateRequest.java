// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2.requests;

import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Id;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Request;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Response;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.MissingResourceException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;

public class NodeStateRequest extends Request<Response.NodeResponse> {
    private final Id.Node id;

    public NodeStateRequest(Id.Node id) {
        super(MasterState.MUST_BE_MASTER);
        this.id = id;
    }

    @Override
    public Response.NodeResponse calculateResult(RemoteClusterControllerTask.Context context) throws StateRestApiException {
        Response.NodeResponse result = new Response.NodeResponse();
        NodeInfo info = context.cluster.getNodeInfo(id.getNode());
        if (info == null) {
            throw new MissingResourceException("node " + id.getNode());
        }

        if (info.getGroup() != null) {
            result.addAttribute("hierarchical-group", info.getGroup().getPath());
        }

        result.addState("generated", new Response.UnitStateImpl(context.currentConsolidatedState.getNodeState(id.getNode())));
        result.addState("unit", new Response.UnitStateImpl(info.getReportedState()));
        result.addState("user", new Response.UnitStateImpl(info.getWantedState()));

        return result;
    }
}
