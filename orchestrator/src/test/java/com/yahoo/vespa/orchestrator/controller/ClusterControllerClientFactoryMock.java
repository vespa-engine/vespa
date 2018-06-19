// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.DummyInstanceLookupService;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mock implementation of ClusterControllerClient
 * <p>
 *
 * @author smorgrav
 */
public class ClusterControllerClientFactoryMock implements ClusterControllerClientFactory {
    Map<String, ClusterControllerNodeState> nodes = new HashMap<>();

    public boolean isInMaintenance(ApplicationInstance appInstance, HostName hostName) {
        try {
            ClusterId clusterName = VespaModelUtil.getContentClusterName(appInstance, hostName);
            int storageNodeIndex = VespaModelUtil.getStorageNodeIndex(appInstance, hostName);
            String globalMapKey = clusterName.s() + storageNodeIndex;
            return nodes.getOrDefault(globalMapKey, ClusterControllerNodeState.UP) == ClusterControllerNodeState.MAINTENANCE;
        } catch (Exception e) {
            //Catch all - meant to catch cases where the node is not part of a storage cluster
            return false;
        }
    }

    public void setAllDummyNodesAsUp() {
        for (ApplicationInstance app : DummyInstanceLookupService.getApplications()) {
            Set<HostName> hosts = DummyInstanceLookupService.getContentHosts(app.reference());
            for (HostName host : hosts) {
                ClusterId clusterName = VespaModelUtil.getContentClusterName(app, host);
                int storageNodeIndex = VespaModelUtil.getStorageNodeIndex(app, host);
                String globalMapKey = clusterName.s() + storageNodeIndex;
                nodes.put(globalMapKey, ClusterControllerNodeState.UP);
            }
        }
    }

    @Override
    public ClusterControllerClient createClient(List<HostName> clusterControllers, String clusterName) {
        return new ClusterControllerClient() {

            @Override
            public ClusterControllerStateResponse setNodeState(OrchestratorContext context, int storageNodeIndex, ClusterControllerNodeState wantedState) throws IOException {
                nodes.put(clusterName + storageNodeIndex, wantedState);
                return new ClusterControllerStateResponse(true, "Yes");
            }

            @Override
            public ClusterControllerStateResponse setApplicationState(OrchestratorContext context, ClusterControllerNodeState wantedState) throws IOException {
                Set<String> keyCopy = new HashSet<>(nodes.keySet());
                for (String s : keyCopy) {
                    if (s.startsWith(clusterName)) {
                        nodes.put(s, wantedState);
                    }
                }
                return new ClusterControllerStateResponse(true, "It works");
            }
        };
    }
}
