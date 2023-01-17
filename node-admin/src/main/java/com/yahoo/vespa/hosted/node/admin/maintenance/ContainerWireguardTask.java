package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;

/**
 * Ensures that wireguard-go is running on the host.
 *
 * @author gjoranv
 */
public interface ContainerWireguardTask {

    void converge(NodeAgentContext context);

}
