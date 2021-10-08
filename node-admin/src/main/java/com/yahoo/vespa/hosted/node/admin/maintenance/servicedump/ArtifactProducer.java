// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.io.IOException;

/**
 * Produces service dump artifacts.
 *
 * @author bjorncs
 */
interface ArtifactProducer {

    String name();

    void produceArtifact(NodeAgentContext context, String configId, ServiceDumpReport.DumpOptions options,
                         UnixPath resultDirectoryInNode) throws IOException;


}
