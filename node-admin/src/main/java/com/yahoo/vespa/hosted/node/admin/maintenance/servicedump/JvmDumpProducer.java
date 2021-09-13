// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates a dump of JVM based Vespa services using vespa-jvm-dumper
 *
 * @author bjorncs
 */
class JvmDumpProducer implements ArtifactProducer {

    private static final Logger log = Logger.getLogger(JvmDumpProducer.class.getName());

    private final ContainerOperations container;

    JvmDumpProducer(ContainerOperations container) { this.container = container; }

    public static String NAME = "jvm-dump";

    @Override public String name() { return NAME; }

    @Override
    public void produceArtifact(NodeAgentContext context, String configId, ServiceDumpReport.DumpOptions options,
                                UnixPath resultDirectoryInNode) throws IOException {
        UnixPath vespaJvmDumper = new UnixPath(context.pathInNodeUnderVespaHome("bin/vespa-jvm-dumper"));
        context.log(log, Level.INFO,
                "Executing '" + vespaJvmDumper + "' with arguments '" + configId + "' and '" + resultDirectoryInNode + "'");
        CommandResult result = container.executeCommandInContainerAsRoot(
                context, vespaJvmDumper.toString(), configId, resultDirectoryInNode.toString());
        context.log(log, Level.INFO,
                "vespa-jvm-dumper exited with code '" + result.getExitCode() + "' and output:\n" + result.getOutput());
        if (result.getExitCode() > 0) {
            throw new IOException("Failed to jvm dump: " + result.getOutput());
        }
    }
}
