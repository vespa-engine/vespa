// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.DummyServiceMonitor;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.model.ContentService;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;

import java.util.HashMap;
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
            String globalMapKey = clusterName + "/" + ContentService.STORAGE_NODE.nameInClusterController() + "/" + storageNodeIndex;
            return nodes.getOrDefault(globalMapKey, ClusterControllerNodeState.UP) == ClusterControllerNodeState.MAINTENANCE;
        } catch (Exception e) {
            //Catch all - meant to catch cases where the node is not part of a storage cluster
            return false;
        }
    }

    public void setAllDummyNodesAsUp() {
        for (ApplicationInstance app : DummyServiceMonitor.getApplications()) {
            Set<HostName> hosts = DummyServiceMonitor.getContentHosts(app.reference());
            for (HostName host : hosts) {
                ClusterId clusterName = VespaModelUtil.getContentClusterName(app, host);
                int storageNodeIndex = VespaModelUtil.getStorageNodeIndex(app, host);
                String globalMapKey = clusterName + "/" + ContentService.STORAGE_NODE.nameInClusterController() + "/" + storageNodeIndex;
                nodes.put(globalMapKey, ClusterControllerNodeState.UP);
            }
        }
    }

    @Override
    public ClusterControllerClient createClient(List<HostName> clusterControllers, String clusterName) {
        return new ClusterControllerClient() {
            @Override public boolean trySetNodeState(OrchestratorContext context, HostName host, int storageNodeIndex, ClusterControllerNodeState wantedState, ContentService contentService, boolean force) {
                nodes.put(clusterName + "/" + contentService.nameInClusterController() + "/" + storageNodeIndex, wantedState);
                return true;
            }
            @Override public void setNodeState(OrchestratorContext context, HostName host, int storageNodeIndex, ClusterControllerNodeState wantedState, ContentService contentService, boolean force) {
                trySetNodeState(context, host, storageNodeIndex, wantedState, contentService, false);
            }
            @Override public void setApplicationState(OrchestratorContext context, ApplicationInstanceId applicationId, ClusterControllerNodeState wantedState) {
                nodes.replaceAll((key, state) -> key.startsWith(clusterName + "/") ? wantedState : state);
            }
        };
    }

}
