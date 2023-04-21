package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.vespa.hosted.node.admin.container.ContainerId;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;

/**
 * Wireguard task for containers.
 *
 * @author gjoranv
 */
public interface ContainerWireguardTask {

    void converge(NodeAgentContext context, ContainerId containerId);

}
