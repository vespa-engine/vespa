// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;

/**
 * @author bjorncs
 */
public interface VespaServiceDumper {
    void processServiceDumpRequest(NodeAgentContext context);

    VespaServiceDumper DUMMY_INSTANCE = context -> {};
}
