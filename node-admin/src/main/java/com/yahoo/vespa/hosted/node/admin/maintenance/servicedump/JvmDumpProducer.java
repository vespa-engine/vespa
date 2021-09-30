// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.io.IOException;
import java.util.List;

/**
 * Creates a dump of JVM based Vespa services using vespa-jvm-dumper
 *
 * @author bjorncs
 */
class JvmDumpProducer extends AbstractProducer {

    JvmDumpProducer(ContainerOperations container) { super(container); }

    public static String NAME = "jvm-dump";

    @Override public String name() { return NAME; }

    @Override
    public void produceArtifact(NodeAgentContext context, String configId, ServiceDumpReport.DumpOptions options,
                                UnixPath resultDirectoryInNode) throws IOException {
        UnixPath vespaJvmDumper = new UnixPath(context.pathInNodeUnderVespaHome("bin/vespa-jvm-dumper"));
        executeCommand(context, List.of(vespaJvmDumper.toString(), configId, resultDirectoryInNode.toString()), true);
    }
}
